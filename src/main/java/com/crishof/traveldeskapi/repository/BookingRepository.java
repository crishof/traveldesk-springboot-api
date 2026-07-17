package com.crishof.traveldeskapi.repository;

import com.crishof.traveldeskapi.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findAllByAgencyIdOrderByCreatedAtDesc(UUID agencyId);

    Optional<Booking> findByIdAndAgencyId(UUID id, UUID agencyId);

    boolean existsByAgencyIdAndReferenceIgnoreCase(UUID agencyId, String reference);

    boolean existsByAgencyIdAndReferenceIgnoreCaseAndIdNot(UUID agencyId, String reference, UUID id);

    boolean existsByCustomerId(UUID customerId);

    boolean existsBySupplierId(UUID supplierId);

    long countByAgencyId(UUID agencyId);

    // Trae de una sola vez (con agency/customer via JOIN FETCH) todos los bookings de un
    // usuario en un estado dado, para agrupar en memoria y evitar el N+1 en el estado de cuenta.
    @Query("SELECT b FROM Booking b " +
            "JOIN FETCH b.agency " +
            "JOIN FETCH b.customer " +
            "WHERE b.createdBy.id = :createdById AND b.status = :status")
    List<Booking> findAllByCreatedByIdAndStatusWithAgencyAndCustomer(
            UUID createdById,
            com.crishof.traveldeskapi.model.BookingStatus status
    );
}
