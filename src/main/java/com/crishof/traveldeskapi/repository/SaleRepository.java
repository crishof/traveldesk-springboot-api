package com.crishof.traveldeskapi.repository;

import com.crishof.traveldeskapi.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findAllByAgencyIdOrderBySaleDateDesc(UUID agencyId);

    Optional<Sale> findByIdAndAgencyId(UUID id, UUID agencyId);

    boolean existsByCustomerId(UUID customerId);

    long countByAgencyId(UUID agencyId);

    @Query("SELECT s FROM Sale s LEFT JOIN FETCH s.payments WHERE s.id = :id AND s.agency.id = :agencyId")
    Optional<Sale> findByIdAndAgencyIdWithPayments(UUID id, UUID agencyId);

    // Fetch-join de asociaciones LAZY (payments/createdBy/customer/agency) para evitar el N+1
    // al calcular comisiones en el estado de cuenta. DISTINCT deduplica por el join con payments.
    @Query("SELECT DISTINCT s FROM Sale s " +
            "LEFT JOIN FETCH s.payments " +
            "LEFT JOIN FETCH s.createdBy " +
            "LEFT JOIN FETCH s.customer " +
            "LEFT JOIN FETCH s.agency " +
            "WHERE s.createdBy.id = :createdById AND s.currency = :currency " +
            "ORDER BY s.saleDate ASC")
    List<Sale> findByCreatedByIdAndCurrencyOrderBySaleDateAsc(UUID createdById, String currency);

}
