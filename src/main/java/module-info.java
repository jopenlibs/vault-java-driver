module vault.java.driver {
    requires java.logging;
    requires java.net.http;
    exports io.github.jopenlibs.vault;
    exports io.github.jopenlibs.vault.api;
    exports io.github.jopenlibs.vault.api.database;
    exports io.github.jopenlibs.vault.api.pki;
    exports io.github.jopenlibs.vault.api.sys;
    exports io.github.jopenlibs.vault.api.sys.mounts;
    exports io.github.jopenlibs.vault.json;
    exports io.github.jopenlibs.vault.response;
    exports io.github.jopenlibs.vault.rest;
}
