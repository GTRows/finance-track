package com.fintrack.analytics.correlation;

/**
 * Method used to compute the pairwise asset return correlation. {@code PEARSON} is the standard
 * linear correlation of log returns; {@code SPEARMAN} ranks the returns first and runs the same
 * Pearson kernel on the ranks (robust to outliers / monotonic-but-non-linear relationships).
 */
public enum CorrelationMethod {
    PEARSON,
    SPEARMAN
}
