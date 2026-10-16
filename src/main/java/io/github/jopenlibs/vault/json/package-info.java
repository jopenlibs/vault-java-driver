/**
 * <p>Intended for internal use by other classes within the Vault driver library.</p>
 *
 * <p>JSON parsing functionality.  The classes in this package are adapted from Ralf Sternberg's
 * excellent <a href="https://github.com/ralfstx/minimal-json">"minimal-json"</a> library, available
 * under the MIT License. The package names have all been changed, to prevent any conflicts should
 * you happen to be using a different version of this library elsewhere in your project
 * dependencies.</p>
 *
 * <p><b>This package is not part of the supported API of the driver.</b> It is exported only
 * because some public methods take or return its types, such as
 * {@link io.github.jopenlibs.vault.response.LogicalResponse#getDataObject()},
 * {@link io.github.jopenlibs.vault.response.UnwrapResponse#getData()},
 * {@link io.github.jopenlibs.vault.api.Auth#wrap(JsonObject, int)},
 * {@link io.github.jopenlibs.vault.api.sys.Wrapping#wrap(JsonObject, int)} and
 * {@link io.github.jopenlibs.vault.api.sys.mounts.MountPayload}. Use {@link Json},
 * {@link JsonObject}, {@link JsonArray}, {@link JsonValue} and {@link ParseException} only as far
 * as those methods need. Other classes, such as {@link JsonParser} and {@link JsonHandler}, are
 * deprecated for use outside the driver. Any class here may change in a minor release, and the
 * package is expected to be removed from the public API in a future major release.</p>
 */
package io.github.jopenlibs.vault.json;
