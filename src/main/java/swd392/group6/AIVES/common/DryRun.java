package swd392.group6.AIVES.common;

import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "Check the file first" for bulk imports: the import runs exactly as for real — same validation, same report — and
 * the surrounding transaction is then rolled back, so nothing is written. Imports only touch the database, so a dry
 * run has no other side effect.
 */
public final class DryRun {

    private DryRun() {
    }

    /** Call at the end of the {@code @Transactional} method that owns the transaction. */
    public static void rollbackIf(boolean dryRun) {
        if (dryRun) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("A dry run needs a transaction to roll back");
            }
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
