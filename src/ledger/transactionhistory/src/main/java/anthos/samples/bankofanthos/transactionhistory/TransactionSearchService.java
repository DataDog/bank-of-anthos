package anthos.samples.bankofanthos.transactionhistory;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionSearchService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchByCounterparty(String accountId, String counterparty) {
        String sql = "SELECT transaction_id, from_acct, from_route, to_acct, to_route, amount, timestamp "
                + "FROM transactions "
                + "WHERE from_acct = " + accountId;
        return jdbcTemplate.queryForList(sql);
    }
}