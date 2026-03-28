package net.aquiles.carryme.config;

public final class ConfigKeys {

    private ConfigKeys() {
    }

    public record PathPair(String current, String legacy) {
    }

    public static final class Settings {
        public static final PathPair MAX_DISTANCE = new PathPair("settings.max-distance", "Configuracion.distancia-maxima");
        public static final PathPair REQUEST_EXPIRATION_SECONDS = new PathPair("settings.request-expiration-seconds", "Configuracion.tiempo-expiracion");
        public static final PathPair PREFIX = new PathPair("settings.prefix", "Configuracion.prefijo");

        private Settings() {
        }
    }

    public static final class Messages {
        private Messages() {
        }

        public static final class Errors {
            public static final PathPair NO_PERMISSION = new PathPair("messages.errors.no-permission", "Textos.Errores.sin-permiso");
            public static final PathPair USAGE = new PathPair("messages.errors.usage", "Textos.Errores.uso-incorrecto");
            public static final PathPair SELF_CARRY = new PathPair("messages.errors.self-carry", "Textos.Errores.auto-carga");
            public static final PathPair TOO_FAR = new PathPair("messages.errors.too-far", "Textos.Errores.muy-lejos");
            public static final PathPair PLAYER_NOT_FOUND = new PathPair("messages.errors.player-not-found", "Textos.Errores.no-encontrado");
            public static final PathPair OCCUPIED = new PathPair("messages.errors.occupied", "Textos.Errores.ocupado");
            public static final PathPair PLAYERS_ONLY = new PathPair("messages.errors.players-only", null);
            public static final PathPair ADMIN_USAGE = new PathPair("messages.errors.admin-usage", null);

            private Errors() {
            }
        }

        public static final class Requests {
            public static final PathPair SENT = new PathPair("messages.requests.sent", "Textos.Peticiones.enviada");
            public static final PathPair RECEIVED = new PathPair("messages.requests.received", "Textos.Peticiones.recibida");
            public static final PathPair ACCEPT_BUTTON = new PathPair("messages.requests.accept-button", "Textos.Peticiones.boton-aceptar");
            public static final PathPair REJECT_BUTTON = new PathPair("messages.requests.reject-button", "Textos.Peticiones.boton-rechazar");
            public static final PathPair EXPIRED = new PathPair("messages.requests.expired", "Textos.Peticiones.expirada");
            public static final PathPair REJECTED = new PathPair("messages.requests.rejected", "Textos.Peticiones.rechazada");

            private Requests() {
            }
        }

        public static final class Actions {
            public static final PathPair CARRYING = new PathPair("messages.actions.carrying", "Textos.Acciones.cargando");
            public static final PathPair BEING_CARRIED = new PathPair("messages.actions.being-carried", "Textos.Acciones.siendo-cargado");
            public static final PathPair DROPPED = new PathPair("messages.actions.dropped", "Textos.Acciones.soltado");
            public static final PathPair RELOADED = new PathPair("messages.actions.reloaded", "Textos.Acciones.recargado");

            private Actions() {
            }
        }
    }

    public static final class CommandAliases {
        private static final String ROOT = "command-aliases.";

        private CommandAliases() {
        }

        public static String path(String commandName) {
            return ROOT + commandName;
        }
    }

    public static final class UpdateCheck {
        public static final String AVAILABLE = "update-check.available";

        private UpdateCheck() {
        }
    }
}
