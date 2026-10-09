package io.poxray.xi;

/**
 * A typed link ({@code p1:lnkRole}/{@code p1:lnk}) from one object to another.
 *
 * @param role   link role, e.g. {@code MAP0}, {@code outboundInterface}, {@code REQUEST_TRAFO}, {@code TYPE}
 * @param kpos   position within the role
 * @param mode   {@code A} absolute (target with own SWCV), {@code R} relative (same SWCV / Directory)
 * @param target referenced object; for relative links the SWCV is inherited from the owning object
 */
public record XiLink(String role, int kpos, String mode, XiRef target) {
}
