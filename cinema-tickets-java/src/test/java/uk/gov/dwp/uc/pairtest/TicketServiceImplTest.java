package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class TicketServiceImplTest {

    private TicketPaymentService paymentService;
    private SeatReservationService reservationService;
    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        paymentService = mock(TicketPaymentService.class);
        reservationService = mock(SeatReservationService.class);
        ticketService = new TicketServiceImpl(paymentService, reservationService);
    }

    @Test
    void shouldProcessPaymentAndReserveSeatsForValidPurchase() {
        // Arrange
        long accountId = 1L;

        TicketTypeRequest adults = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest children = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 3);
        TicketTypeRequest infants = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);

        // Act
        ticketService.purchaseTickets(accountId, adults, children, infants);

        // Assert
        verify(paymentService).makePayment(accountId, 95);
        verify(reservationService).reserveSeat(accountId, 5);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void shouldRejectPurchaseWhenAccountIdIsInvalid(Long accountId) {
        // Arrange
        TicketTypeRequest adults = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, adults));
        verifyNoInteractions(paymentService, reservationService);
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 25})
    void shouldAcceptPurchaseWithinTicketLimit(int quantity) {
        // Arrange
        long accountId = 1L;
        TicketTypeRequest adults = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, quantity);

        // Act
        ticketService.purchaseTickets(accountId, adults);

        // Assert
        verify(paymentService).makePayment(accountId, quantity * 25);
        verify(reservationService).reserveSeat(accountId, quantity);
    }

    @Test
    void shouldRejectPurchaseExceedingTicketLimit() {
        // Arrange
        long accountId = 1L;
        TicketTypeRequest adults = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 26);

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, adults));
        verifyNoInteractions(paymentService, reservationService);
    }

    @Test
    void shouldRejectPurchaseExceedingLimitWithCombindRequests() {
        // Arrange
        long accountId = 1L;
        TicketTypeRequest adults = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 10);
        TicketTypeRequest children = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 10);
        TicketTypeRequest infants = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 6);

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, adults, children, infants));
        verifyNoInteractions(paymentService, reservationService);
    }

    @ParameterizedTest
    @MethodSource("ticketRequestWithoutAdults")
    void shouldRejectPurchaseWithoutAdultTickets(TicketTypeRequest[] requests){
        // Arrange
        long accountId = 1L;

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, requests));
        verifyNoInteractions(paymentService, reservationService);
    }


    private static Stream<Arguments> ticketRequestWithoutAdults() {
        return Stream.of(
                Arguments.of((Object) new TicketTypeRequest[]{
                        new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1)
                }),
                Arguments.of((Object) new TicketTypeRequest[]{
                        new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1)
                }),
                Arguments.of((Object) new TicketTypeRequest[]{
                        new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1),
                        new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1)
                })
        );
    }

    @Test
    void shouldNotChargeOrReserveSeatForInfant() {
        // Arrange
        long accountId = 1L;

        TicketTypeRequest adults = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest infants = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 2);

        // Act
        ticketService.purchaseTickets(accountId, adults, infants);

        // Assert
        verify(paymentService).makePayment(accountId, 25);
        verify(reservationService).reserveSeat(accountId, 1);
    }

    @Test
    void shouldRejectNullTicketRequestArray() {
        // Arrange
        long accountId = 1L;

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, (TicketTypeRequest[]) null));
        verifyNoInteractions(paymentService, reservationService);
    }

    @Test
    void shouldRejectAnyNullTicketRequest() {
        // Arrange
        long accountId = 1L;

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId,
                        new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1),
                        null));
        verifyNoInteractions(paymentService, reservationService);
    }

    @Test
    void shouldRejectEmptyTicketRequest() {
        // Arrange
        long accountId = 1L;

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId));
        verifyNoInteractions(paymentService, reservationService);
    }

    @Test
    void shouldRejectNullTicketType() {
        // Arrange
        long accountId = 1L;
        TicketTypeRequest request = new TicketTypeRequest(null, 2);

        // Act and assert
        assertThrows(
                InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, request));
        verifyNoInteractions(paymentService, reservationService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectInvalidTicketQuantities(int quantity) {
        // Arrange
        long accountId = 1L;
        TicketTypeRequest request = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, quantity);

        // Act and assert
        assertThrows(InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(accountId, request));
        verifyNoInteractions(paymentService, reservationService);
    }
}
