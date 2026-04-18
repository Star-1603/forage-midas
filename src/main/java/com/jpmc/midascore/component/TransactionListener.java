package com.jpmc.midascore.component;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.messaging.handler.annotation.Payload;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.UserRepository;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.MidasCoreApplication;
import java.util.Optional;
import org.springframework.web.client.RestTemplate;
@Component
public class TransactionListener {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public TransactionListener(UserRepository userRepository,
                               TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @KafkaListener(topics = "${general.kafka-topic}")
public void listen(@Payload Transaction transaction) {

    Optional<UserRecord> senderOpt = userRepository.findById(transaction.getSenderId());
    Optional<UserRecord> recipientOpt = userRepository.findById(transaction.getRecipientId());

    if (senderOpt.isEmpty() || recipientOpt.isEmpty()) return;

    UserRecord sender = senderOpt.get();
    UserRecord recipient = recipientOpt.get();

    if (sender.getBalance() < transaction.getAmount()) return;

    // 🔥 CALL INCENTIVE API
    String url = "http://localhost:33400/incentive";
    Incentive incentive = restTemplate.postForObject(url, transaction, Incentive.class);

    float incentiveAmount = (incentive != null) ? incentive.getAmount() : 0;

    // 💰 UPDATE BALANCES
    sender.setBalance(sender.getBalance() - transaction.getAmount());

    recipient.setBalance(
        recipient.getBalance() + transaction.getAmount() + incentiveAmount
    );

    userRepository.save(sender);
    userRepository.save(recipient);

    // 🧾 SAVE TRANSACTION
    TransactionRecord record = new TransactionRecord();
    record.setAmount(transaction.getAmount());
    record.setSender(sender);
    record.setRecipient(recipient);
    record.setIncentive(incentive.getAmount());

    transactionRepository.save(record);
    userRepository.findAll().forEach(u -> {
        if (u.getName().equals("wilbur")) {
            System.out.println("WILBUR FINAL BALANCE: " + u.getBalance());
        }
    });
}
    }