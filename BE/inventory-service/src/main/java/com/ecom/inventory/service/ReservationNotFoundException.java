package com.ecom.inventory.service;

import com.ecom.common.web.NotFoundException;

import java.util.UUID;

public class ReservationNotFoundException extends NotFoundException {
    public ReservationNotFoundException(UUID reservationId) {
        super("Reservation", reservationId);
    }
}
