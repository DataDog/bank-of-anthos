/*
 * Copyright 2020, Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package anthos.samples.bankofanthos.transactionhistory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<?> searchByCounterparty(String accountId, String counterparty) {
        String sql = "SELECT transaction_id, from_acct, from_route, to_acct, to_route, amount, timestamp "
                + "FROM transactions "
                + "WHERE (from_acct = '" + accountId + "' OR to_acct = '" + accountId + "') "
                + "AND (from_acct = '" + counterparty + "' OR to_acct = '" + counterparty + "')";
        return entityManager.createNativeQuery(sql).getResultList();
    }
}
