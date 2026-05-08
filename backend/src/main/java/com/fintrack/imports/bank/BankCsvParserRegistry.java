package com.fintrack.imports.bank;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@link BankCsvParser} from a {@link Bank} enum value. Spring autowires every {@link
 * BankCsvParser} bean and the registry indexes them by their declared {@link BankCsvParser#bank()}.
 */
@Component
public class BankCsvParserRegistry {

    private final Map<Bank, BankCsvParser> parsers;

    public BankCsvParserRegistry(List<BankCsvParser> parserList) {
        this.parsers = new EnumMap<>(Bank.class);
        for (BankCsvParser p : parserList) {
            parsers.put(p.bank(), p);
        }
    }

    public BankCsvParser get(Bank bank) {
        BankCsvParser p = parsers.get(bank);
        if (p == null) {
            throw new IllegalArgumentException("No parser registered for bank: " + bank);
        }
        return p;
    }
}
