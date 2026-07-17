package com.crishof.traveldeskapi.service;

import com.crishof.traveldeskapi.dto.AccountMovementDTO;
import com.crishof.traveldeskapi.dto.AccountPaymentRequest;
import com.crishof.traveldeskapi.dto.AccountStatementDTO;
import com.crishof.traveldeskapi.exception.InvalidRequestException;
import com.crishof.traveldeskapi.exception.ResourceNotFoundException;
import com.crishof.traveldeskapi.model.*;
import com.crishof.traveldeskapi.repository.AccountPaymentRepository;
import com.crishof.traveldeskapi.repository.BookingRepository;
import com.crishof.traveldeskapi.repository.SaleRepository;
import com.crishof.traveldeskapi.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountStatementServiceImpl implements AccountStatementService {

    private final SaleRepository saleRepository;
    private final BookingRepository bookingRepository;
    private final AccountPaymentRepository accountPaymentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public AccountStatementDTO getStatement(UUID userId, Currency currency) {
        validateUserId(userId);

        getUserOrThrow(userId);
        List<AccountMovementDTO> movements = new ArrayList<>();

        List<Sale> sales = saleRepository.findByCreatedByIdAndCurrencyOrderBySaleDateAsc(userId, currency.name());

        // Se cargan los bookings PAID del usuario en una sola query y se agrupan en memoria
        // por (agencia, cliente, fecha de salida) para evitar el N+1 dentro del bucle de ventas.
        Map<BookingGroupKey, BigDecimal> paidBookingTotals = loadPaidBookingTotals(userId, currency);

        for (Sale sale : sales) {
            BigDecimal commissionAmount = calculateCommissionAmount(sale, currency, paidBookingTotals);

            if (commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            AccountMovementDTO movement = new AccountMovementDTO();
            movement.setId(sale.getId());
            movement.setDate(resolveSaleMovementDate(sale));
            movement.setType(MovementType.SALE_FEE);
            movement.setSaleId(sale.getId());
            movement.setConcept(resolveSaleConcept(sale));
            movement.setAmount(commissionAmount);
            movement.setCurrency(currency);

            movements.add(movement);
        }

        List<AccountPayment> payments = accountPaymentRepository.findByUserIdAndCurrencyOrderByDateAscIdAsc(userId, currency);

        for (AccountPayment payment : payments) {
            AccountMovementDTO movement = new AccountMovementDTO();
            movement.setId(payment.getId());
            movement.setDate(resolvePaymentDate(payment));
            movement.setType(MovementType.MANUAL_PAYMENT);
            movement.setConcept(resolvePaymentDescription(payment));
            movement.setAmount(payment.getAmount().negate());
            movement.setCurrency(payment.getCurrency());

            movements.add(movement);
        }

        movements.sort(
                Comparator
                        .comparing(AccountMovementDTO::getDate, Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(movement -> String.valueOf(movement.getId()), Comparator.nullsLast(String::compareTo))
        );

        BigDecimal balance = movements.stream()
                .map(AccountMovementDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AccountStatementDTO.builder()
                .currency(currency)
                .balance(balance)
                .movements(movements)
                .build();
    }

    @Override
    @Transactional
    public AccountStatementDTO addPayment(UUID userId, AccountPaymentRequest request) {
        validateUserId(userId);
        User user = getUserOrThrow(userId);

        AccountPayment payment = new AccountPayment();
        payment.setUserId(userId);
        payment.setAgencyId(user.getAgency().getId());
        payment.setDate(request.date());
        payment.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        payment.setCurrency(request.currency());
        payment.setDescription(normalizeDescription(request.description()));

        accountPaymentRepository.save(payment);
        return getStatement(userId, request.currency());
    }

    @Override
    @Transactional
    public AccountStatementDTO updatePayment(UUID userId, UUID paymentId, AccountPaymentRequest request) {
        validateUserId(userId);
        validatePaymentId(paymentId);
        getUserOrThrow(userId);

        AccountPayment payment = getPaymentOrThrow(paymentId, userId);
        payment.setDate(request.date());
        payment.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        payment.setCurrency(request.currency());
        payment.setDescription(normalizeDescription(request.description()));

        accountPaymentRepository.save(payment);
        return getStatement(userId, request.currency());
    }

    @Override
    @Transactional
    public AccountStatementDTO deletePayment(UUID userId, UUID paymentId) {
        validateUserId(userId);
        validatePaymentId(paymentId);
        getUserOrThrow(userId);

        AccountPayment payment = getPaymentOrThrow(paymentId, userId);
        Currency paymentCurrency = payment.getCurrency();

        accountPaymentRepository.delete(payment);
        return getStatement(userId, paymentCurrency);
    }

    /**
     * Agrupa los bookings PAID del usuario (para la moneda dada) por (agencia, cliente, fecha de salida),
     * sumando su importe. Equivale a lo que antes hacia una consulta por venta dentro del bucle.
     */
    private Map<BookingGroupKey, BigDecimal> loadPaidBookingTotals(UUID userId, Currency currency) {
        return bookingRepository
                .findAllByCreatedByIdAndStatusWithAgencyAndCustomer(userId, BookingStatus.PAID)
                .stream()
                .filter(booking -> currency.name().equalsIgnoreCase(booking.getCurrency()))
                .filter(booking -> booking.getAgency() != null
                        && booking.getCustomer() != null
                        && booking.getDepartureDate() != null)
                .collect(Collectors.groupingBy(
                        booking -> new BookingGroupKey(
                                booking.getAgency().getId(),
                                booking.getCustomer().getId(),
                                booking.getDepartureDate()),
                        Collectors.reducing(BigDecimal.ZERO, booking -> safeAmount(booking.getAmount()), BigDecimal::add)));
    }

    private BigDecimal calculateCommissionAmount(Sale sale, Currency currency,
                                                 Map<BookingGroupKey, BigDecimal> paidBookingTotals) {
        if (sale == null || sale.getCreatedBy() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal commissionPercentage = sale.getCommissionPercentage() != null
            ? safeAmount(sale.getCommissionPercentage())
            : safeAmount(sale.getCreatedBy().getCommissionPercentage());
        if (commissionPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalPaymentsReceived = safePayments(sale).stream()
            .map(Payment::getConvertedAmount)
            .map(this::safeAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sale.getAgency() == null || sale.getCustomer() == null || sale.getDepartureDate() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalBookings = paidBookingTotals.getOrDefault(
                new BookingGroupKey(
                        sale.getAgency().getId(),
                        sale.getCustomer().getId(),
                        sale.getDepartureDate()),
                BigDecimal.ZERO);

        BigDecimal netProfit = totalPaymentsReceived.subtract(totalBookings);
        if (netProfit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return netProfit
                .multiply(commissionPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Clave de agrupacion de bookings: coincide con los filtros de la consulta por venta original. */
    private record BookingGroupKey(UUID agencyId, UUID customerId, java.time.LocalDate departureDate) {
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String resolvePaymentDescription(AccountPayment payment) {
        if (payment.getDescription() != null && !payment.getDescription().isBlank()) {
            return payment.getDescription();
        }
        return "Cobro de comisiones";
    }

    private LocalDate resolveSaleMovementDate(Sale sale) {
        if (sale.getSaleDate() == null) {
            return LocalDate.now();
        }
        return sale.getSaleDate().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String resolveSaleConcept(Sale sale) {
        String customerName = sale.getCustomer() != null
                ? Objects.toString(sale.getCustomer().getFullName(), "Cliente sin nombre")
                : "Cliente sin nombre";
        String destination = Objects.toString(sale.getDestination(), "Sin destino");
        return "Comision venta " + customerName + " - " + destination;
    }

    private LocalDate resolvePaymentDate(AccountPayment payment) {
        return payment.getDate() == null ? LocalDate.now() : payment.getDate();
    }

    private List<Payment> safePayments(Sale sale) {
        return sale.getPayments() == null ? List.of() : sale.getPayments();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "Cobro de comisiones";
        }
        return description.trim().replaceAll("\\s+", " ");
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private AccountPayment getPaymentOrThrow(UUID paymentId, UUID userId) {
        return accountPaymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new InvalidRequestException("User id is required");
        }
    }

    private void validatePaymentId(UUID paymentId) {
        if (paymentId == null) {
            throw new InvalidRequestException("Payment id is required");
        }
    }
}