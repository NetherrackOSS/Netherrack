package com.netherrack.server.plugin;

import com.netherrack.api.form.Form;
import com.netherrack.api.form.SimpleForm;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Checks the server side of the form API without starting a network listener.
 * <p>
 * PlayerAdapter is deliberately package-private, so this harness lives in the plugin package too.
 * Unsafe avoids building a real login session just to observe the one packet a form sends.
 */
public final class FormApiTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        ServerAdapter apiServer = allocate(ServerAdapter.class);
        set(apiServer, "nextFormId", new AtomicInteger());

        com.netherrack.server.player.Player serverPlayer =
                allocate(com.netherrack.server.player.Player.class);
        CapturingSession session = allocate(CapturingSession.class);
        set(serverPlayer, "session", session);

        PlayerAdapter adapter = allocate(PlayerAdapter.class);
        set(adapter, "player", serverPlayer);
        set(adapter, "apiServer", apiServer);
        set(adapter, "openForms", new HashMap<Integer, Form>());

        List<String> calls = new ArrayList<>();
        SimpleForm form = new SimpleForm("Test", "Test")
                .addButton("Test")
                .onSubmit((submitter, response) -> calls.add(
                        "submit:" + response.getButtonId() + ":" + response.getButtonText()
                                + ":" + (submitter == adapter)))
                .onClose(submitter -> calls.add("close:" + (submitter == adapter)));

        int id = adapter.showForm(form);
        check("first form id", id, 1);
        check("request packet sent", session.sent instanceof ModalFormRequestPacket, true);
        ModalFormRequestPacket request = (ModalFormRequestPacket) session.sent;
        check("request form id", request.getFormId(), 1);
        check("request JSON", request.getFormData(), form.toJson());
        check("second form refused", adapter.showForm(new SimpleForm("Other", "Other")), -1);

        check("stale response ignored", adapter.completeForm(id + 99, "0", false), null);
        check("submit response", adapter.completeForm(id, "0", false), form);
        check("submit callback", calls, List.of("submit:0:Test:true"));

        SimpleForm closed = new SimpleForm("Close", "Close").addButton("Close")
                .onClose(submitter -> calls.add("closed-again"));
        int closedId = adapter.showForm(closed);
        check("form reopens after answer", closedId, 2);
        check("closed response", adapter.completeForm(closedId, "null", false), closed);
        check("close callback", calls, List.of("submit:0:Test:true", "closed-again"));

        SimpleForm abandoned = new SimpleForm("Leave", "Leave").addButton("Leave")
                .onClose(submitter -> calls.add("must-not-run"));
        adapter.showForm(abandoned);
        adapter.discardForm();
        check("disconnect clears form", adapter.completeForm(3, "0", false), null);
        check("disconnect skips callbacks", calls, List.of("submit:0:Test:true", "closed-again"));

        System.out.println(failures == 0
                ? "All form API checks passed."
                : failures + " check(s) failed.");
        if (failures > 0) {
            System.exit(1);
        }
    }

    /** Captures the request the adapter sends, without needing a connected peer. */
    private static final class CapturingSession extends BedrockServerSession {
        private BedrockPacket sent;

        // Never actually called: the harness allocates this class without a constructor, but Java
        // still needs a declared one that satisfies BedrockServerSession's signature.
        private CapturingSession() {
            super(null, 0);
        }

        @Override
        public void sendPacket(BedrockPacket packet) {
            sent = packet;
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        @SuppressWarnings("unchecked")
        T instance = (T) unsafe.allocateInstance(type);
        return instance;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                sun.misc.Unsafe unsafe = unsafe();
                unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static void check(String name, Object actual, Object expected) {
        if (!java.util.Objects.equals(actual, expected)) {
            failures++;
            System.out.println("FAIL " + name + ": expected <" + expected + "> but got <"
                    + actual + ">");
        } else {
            System.out.println("ok   " + name);
        }
    }
}
