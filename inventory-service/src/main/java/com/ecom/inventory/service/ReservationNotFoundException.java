package com.ecom.inventory.service;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(UUID reservationId) {
        super("Reservation not found: " + reservationId);
    }
}
