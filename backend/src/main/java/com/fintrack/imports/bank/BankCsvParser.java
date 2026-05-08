package com.fintrack.imports.bank;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Per-bank CSV parser. Implementations encode bank-specific quirks (encoding, delimiter, date
 * format, decimal locale, sign convention, header/footer rows). Returns one {@link RawBankRow} per
 * data line; unparseable rows surface with a non-null {@code warning} string instead of raising.
 * File-level read failures (encoding rejection, format mismatch on header sniff) raise {@link
 * IllegalArgumentException}.
 *
 * <p>Implementations also expose a package-private overload {@code parse(InputStream, Charset)}
 * that lets unit tests feed UTF-8 fixtures regardless of the bank's wire encoding. The public
 * {@link #parse(InputStream)} delegates to the overload with {@link #charset()}.
 */
public interface BankCsvParser {
    Bank bank();

    Charset charset();

    List<RawBankRow> parse(InputStream input);
}
