package net.illunium.lumen.tickets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.illunium.lumen.storage.TicketRepository.Status;
import net.illunium.lumen.storage.TicketRepository.Ticket;
import net.illunium.lumen.storage.TicketRepository.Type;
import org.junit.jupiter.api.Test;

class TicketsTest {

    @Test
    void ticketIdIsThePaddedChannelName() {
        assertEquals("ticket-0042", ticket(42).name());
        assertEquals("ticket-0001", ticket(1).name());
        assertEquals("ticket-12345", ticket(12345).name(),
                "past four digits the number simply grows");
    }

    @Test
    void panelButtonsCarryTheirTypeAndAreRoutableByTheRouter() {
        for (Type type : Type.values()) {
            String id = TicketCommand.CREATE + ":" + type.name();
            assertTrue(id.startsWith("ticket:"), id);
            assertEquals(Optional.of(type), TicketCommand.parseType(id));
        }
        assertTrue(TicketCommand.CLAIM.startsWith("ticket:"));
        assertTrue(TicketCommand.CLOSE.startsWith("ticket:"));
    }

    @Test
    void aForgedPanelButtonOpensNoTicket() {
        // A panel message outlives the build that posted it, and the ID is client-supplied.
        assertEquals(Optional.empty(), TicketCommand.parseType("ticket:create:ADMIN"));
        assertEquals(Optional.empty(), TicketCommand.parseType("ticket:create:"));
        assertEquals(Optional.empty(), TicketCommand.parseType("ticket:create"));
        assertEquals(Optional.empty(), TicketCommand.parseType("ticket:claim"));
        assertEquals(Optional.empty(), TicketCommand.parseType("roles:pick"));
        assertEquals(Optional.empty(), TicketCommand.parseType("ticket:create:support"),
                "the value is the enum constant, not a label");
    }

    @Test
    void addingParticipantsStaysWithStaff() {
        Set<String> staffOnly = new TicketCommand(null, null, null).staffOnlySubcommands();
        assertTrue(staffOnly.contains("add"));
        assertTrue(staffOnly.contains("remove"));
        assertTrue(staffOnly.contains("panel create"));
        assertFalse(staffOnly.contains("create"), "anyone may open a ticket");
        assertFalse(staffOnly.contains("close"),
                "the creator closes their own ticket; the handler checks that");
    }

    @Test
    void transcriptIsEmptyButWellFormedWithoutMessages() {
        String text = TicketCommand.transcript(ticket(7), List.of());
        assertTrue(text.startsWith("ticket-0007 (Support)"), text);
        assertTrue(text.contains("Ersteller: 99"), text);
        assertTrue(text.contains("Nachrichten: 0"), text);
    }

    @Test
    void closedTicketsAreRecognisedRegardlessOfClaimState() {
        assertFalse(ticket(1).closed());
        assertFalse(new Ticket(1, 2, 3, Type.REPORT, Status.CLAIMED, 4L).closed());
        assertTrue(new Ticket(1, 2, 3, Type.REPORT, Status.CLOSED, 4L).closed());
    }

    private static Ticket ticket(long id) {
        return new Ticket(id, 500L, 99L, Type.SUPPORT, Status.OPEN, null);
    }
}
