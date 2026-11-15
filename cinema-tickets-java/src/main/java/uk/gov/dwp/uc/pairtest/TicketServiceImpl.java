package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Arrays;

public class TicketServiceImpl implements TicketService {

    private static final int MAX_TICKETS = 25;

    private static final int ADULT_TICKET_PRICE = 25;
    private static final int CHILD_TICKET_PRICE = 15;

    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    public TicketServiceImpl(
            TicketPaymentService ticketPaymentService,
            SeatReservationService seatReservationService) {
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        // Validate the purchase
        validateAccountId(accountId);
        validateTicketRequests(ticketTypeRequests);
        validateHasAdultTicket(ticketTypeRequests);

        // Calculate the payment amount and number of seats
        PurchaseTotals totals = calculatePurchaseTotals(ticketTypeRequests);

        // Process payment
        ticketPaymentService.makePayment(accountId, totals.totalCharge());

        // Reserve seats
        seatReservationService.reserveSeat(accountId, totals.totalSeats());
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Invalid account ID: %s. Account ID must be greater than zero and not null"
                    .formatted(accountId));
        }
    }

    private void validateTicketRequests(TicketTypeRequest... ticketTypeRequests) {
        long totalTickets = 0;

        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket request is required.");
        }

        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null) {
                throw new InvalidPurchaseException("Ticket request must not be null.");
            }

            if (request.getTicketType() == null) {
                throw new InvalidPurchaseException("Ticket type must not be null.");
            }

            if (request.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException("Invalid ticket quantity: %d. Quantity must be greater than zero."
                        .formatted(request.getNoOfTickets()));
            }

            totalTickets += request.getNoOfTickets();

            if (totalTickets > MAX_TICKETS) {
                throw new InvalidPurchaseException("Ticket limit exceeded: %d. A maximum of %d tickets can be purchased."
                        .formatted(totalTickets, MAX_TICKETS));
            }

        }
    }

    private void validateHasAdultTicket(TicketTypeRequest... ticketTypeRequests) {
        boolean hasAdultTicket = Arrays.stream(ticketTypeRequests)
                .anyMatch(request -> request.getTicketType() == TicketTypeRequest.Type.ADULT);

        if (!hasAdultTicket) {
            throw new InvalidPurchaseException("At least one adult ticket is required when purchasing child or infant tickets.");
        }
    }

    private PurchaseTotals calculatePurchaseTotals(TicketTypeRequest... ticketTypeRequest) {
        int totalCharge = 0;
        int totalSeats = 0;

        for (TicketTypeRequest request : ticketTypeRequest) {
            switch (request.getTicketType()) {
                case ADULT -> {
                    totalCharge += request.getNoOfTickets() * ADULT_TICKET_PRICE;
                    totalSeats += request.getNoOfTickets();
                }
                case CHILD -> {
                    totalCharge += request.getNoOfTickets() * CHILD_TICKET_PRICE;
                    totalSeats += request.getNoOfTickets();
                }
                case INFANT -> {
                    // Infants are free and don't require a seat.
                }
            }
        }

        return new PurchaseTotals(totalCharge, totalSeats);
    }

    private record PurchaseTotals(int totalCharge, int totalSeats) {
    }

}
