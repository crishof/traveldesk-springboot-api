package com.crishof.traveldeskapi.service;

import com.crishof.traveldeskapi.dto.AccountMovementDTO;
import com.crishof.traveldeskapi.dto.AccountStatementDTO;
import com.crishof.traveldeskapi.model.Booking;
import com.crishof.traveldeskapi.model.BookingStatus;
import com.crishof.traveldeskapi.model.Currency;
import com.crishof.traveldeskapi.model.Customer;
import com.crishof.traveldeskapi.model.MovementType;
import com.crishof.traveldeskapi.model.Payment;
import com.crishof.traveldeskapi.model.Role;
import com.crishof.traveldeskapi.model.Sale;
import com.crishof.traveldeskapi.model.SaleStatus;
import com.crishof.traveldeskapi.model.User;
import com.crishof.traveldeskapi.model.UserStatus;
import com.crishof.traveldeskapi.model.agency.Agency;
import com.crishof.traveldeskapi.repository.AccountPaymentRepository;
import com.crishof.traveldeskapi.repository.AgencyRepository;
import com.crishof.traveldeskapi.repository.BookingRepository;
import com.crishof.traveldeskapi.repository.CustomerRepository;
import com.crishof.traveldeskapi.repository.SaleRepository;
import com.crishof.traveldeskapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test de caracterizacion del estado de cuenta. Fija el comportamiento actual del calculo
 * de comisiones (comision = (pagos recibidos - bookings PAID de la misma moneda) * % / 100)
 * para poder refactorizar con seguridad el N+1 de la consulta de bookings por venta.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountStatementServiceImplTests {

    @Autowired
    private AccountStatementService accountStatementService;
    @Autowired
    private AgencyRepository agencyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private AccountPaymentRepository accountPaymentRepository;

    // Cada test corre en su propia transaccion con rollback (@Transactional), por lo que no
    // hace falta limpiar manualmente y no interfiere con datos de otras clases de test.

    @Test
    void statementComputesCommissionNetOfBookingsAndSubtractsManualPayments() {
        Agency agency = saveAgency("Agency A", "agency-a");
        User user = saveUser(agency, "seller@example.com", new BigDecimal("10.00"));
        Customer c1 = saveCustomer(agency, "Customer One", "c1@example.com");
        Customer c2 = saveCustomer(agency, "Customer Two", "c2@example.com");

        LocalDate d1 = LocalDate.of(2026, 3, 10);
        LocalDate d2 = LocalDate.of(2026, 4, 20);

        // Venta 1: pagos 1000 USD, booking PAID 400 USD => neto 600 * 10% = 60.00
        saveSaleWithPayment(agency, c1, user, d1, "USD", new BigDecimal("1000.00"));
        saveBooking(agency, c1, user, d1, "USD", new BigDecimal("400.00"), BookingStatus.PAID, "B1");
        // Booking en EUR para el mismo cliente/fecha: debe IGNORARSE por el filtro de moneda.
        saveBooking(agency, c1, user, d1, "EUR", new BigDecimal("999.00"), BookingStatus.PAID, "B1EUR");

        // Venta 2: pagos 500 USD, booking PAID 100 USD => neto 400 * 10% = 40.00
        saveSaleWithPayment(agency, c2, user, d2, "USD", new BigDecimal("500.00"));
        saveBooking(agency, c2, user, d2, "USD", new BigDecimal("100.00"), BookingStatus.PAID, "B2");

        // Cobro manual de 25 USD => movimiento de -25.00
        saveAccountPayment(user.getId(), agency.getId(), new BigDecimal("25.00"), Currency.USD, LocalDate.of(2026, 5, 1));

        AccountStatementDTO statement = accountStatementService.getStatement(user.getId(), Currency.USD);

        assertEquals(Currency.USD, statement.getCurrency());
        // 60.00 + 40.00 - 25.00 = 75.00
        assertEquals(0, statement.getBalance().compareTo(new BigDecimal("75.00")),
                "Balance esperado 75.00 pero fue " + statement.getBalance());

        List<AccountMovementDTO> movements = statement.getMovements();
        assertEquals(3, movements.size(), "Se esperaban 3 movimientos");

        BigDecimal feeSum = movements.stream()
                .filter(m -> m.getType() == MovementType.SALE_FEE)
                .map(AccountMovementDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, feeSum.compareTo(new BigDecimal("100.00")),
                "La suma de comisiones deberia ser 60.00 + 40.00 = 100.00 pero fue " + feeSum);

        BigDecimal manualSum = movements.stream()
                .filter(m -> m.getType() == MovementType.MANUAL_PAYMENT)
                .map(AccountMovementDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, manualSum.compareTo(new BigDecimal("-25.00")),
                "El cobro manual deberia ser -25.00 pero fue " + manualSum);
    }

    @Test
    void statementIsIsolatedPerUserAndAgency() {
        Agency agencyA = saveAgency("Agency A", "agency-a");
        Agency agencyB = saveAgency("Agency B", "agency-b");
        User userA = saveUser(agencyA, "a@example.com", new BigDecimal("10.00"));
        User userB = saveUser(agencyB, "b@example.com", new BigDecimal("10.00"));
        Customer custB = saveCustomer(agencyB, "Customer B", "custb@example.com");
        LocalDate d = LocalDate.of(2026, 6, 1);

        // Datos de la agencia A (usuario A)
        saveAccountPayment(userA.getId(), agencyA.getId(), new BigDecimal("30.00"), Currency.USD, d);

        // Datos de la agencia B (usuario B): NO deben aparecer en el estado de cuenta de A.
        saveAccountPayment(userB.getId(), agencyB.getId(), new BigDecimal("99.00"), Currency.USD, d);
        saveSaleWithPayment(agencyB, custB, userB, d, "USD", new BigDecimal("5000.00"));

        AccountStatementDTO statementA = accountStatementService.getStatement(userA.getId(), Currency.USD);

        // Solo el cobro de A: balance -30.00, un movimiento, y nada de B (99.00).
        assertEquals(1, statementA.getMovements().size());
        assertEquals(0, statementA.getBalance().compareTo(new BigDecimal("-30.00")),
                "El estado de cuenta de A no debe incluir datos de la agencia B");
        boolean leaksFromB = statementA.getMovements().stream()
                .anyMatch(m -> m.getAmount().abs().compareTo(new BigDecimal("99.00")) == 0);
        assertEquals(false, leaksFromB, "Fuga detectada: un movimiento de la agencia B apareció en A");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Agency saveAgency(String name, String normalizedName) {
        Agency agency = new Agency();
        agency.setName(name);
        agency.setNormalizedName(normalizedName);
        return agencyRepository.save(agency);
    }

    private User saveUser(Agency agency, String email, BigDecimal commissionPercentage) {
        User user = new User();
        user.setFullName("Seller");
        user.setEmail(email);
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setAgency(agency);
        user.setCommissionPercentage(commissionPercentage);
        return userRepository.save(user);
    }

    private Customer saveCustomer(Agency agency, String fullName, String email) {
        Customer customer = new Customer();
        customer.setAgency(agency);
        customer.setFullName(fullName);
        customer.setEmail(email);
        return customerRepository.save(customer);
    }

    private void saveSaleWithPayment(Agency agency, Customer customer, User user,
                                     LocalDate departureDate, String currency, BigDecimal convertedAmount) {
        Sale sale = new Sale();
        sale.setAgency(agency);
        sale.setCustomer(customer);
        sale.setCreatedBy(user);
        sale.setDestination("Destination");
        sale.setAmount(new BigDecimal("1000.00"));
        sale.setCurrency(currency);
        sale.setStatus(SaleStatus.CREATED);
        sale.setSaleDate(Instant.now());
        sale.setDepartureDate(departureDate);
        sale.setCommissionPercentage(new BigDecimal("10.00"));

        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setOriginalAmount(convertedAmount);
        payment.setSourceCurrency(currency);
        payment.setExchangeRate(BigDecimal.ONE);
        payment.setConvertedAmount(convertedAmount);
        sale.getPayments().add(payment);

        saleRepository.save(sale);
    }

    private void saveBooking(Agency agency, Customer customer, User user, LocalDate departureDate,
                             String currency, BigDecimal amount, BookingStatus status, String reference) {
        Booking booking = new Booking();
        booking.setAgency(agency);
        booking.setCustomer(customer);
        booking.setCreatedBy(user);
        booking.setReference(reference);
        booking.setAmount(amount);
        booking.setCurrency(currency);
        booking.setDepartureDate(departureDate);
        booking.setStatus(status);
        bookingRepository.save(booking);
    }

    private void saveAccountPayment(UUID userId, UUID agencyId, BigDecimal amount, Currency currency, LocalDate date) {
        com.crishof.traveldeskapi.model.AccountPayment payment = new com.crishof.traveldeskapi.model.AccountPayment();
        payment.setUserId(userId);
        payment.setAgencyId(agencyId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setDate(date);
        payment.setDescription("Cobro");
        accountPaymentRepository.save(payment);
    }
}
