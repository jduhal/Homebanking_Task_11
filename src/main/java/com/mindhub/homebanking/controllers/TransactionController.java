package com.mindhub.homebanking.controllers;

import com.mindhub.homebanking.models.Account;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.models.Transaction;
import com.mindhub.homebanking.models.TransactionType;
import com.mindhub.homebanking.services.AccountService;
import com.mindhub.homebanking.services.ClientService;
import com.mindhub.homebanking.services.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private AccountService accountService;

    @Transactional
    @PostMapping(path = "/transactions")
    public ResponseEntity<Object> makeTransaction (@RequestParam double amount,
                                                   @RequestParam String description,
                                                   @RequestParam String fromAccountNumber,
                                                   @RequestParam String toAccountNumber,
                                                   Authentication authentication) {

        //Checking not null parameters
        if (amount <= 0 || description.isEmpty() || fromAccountNumber.isEmpty() || toAccountNumber.isEmpty()) {
            return new ResponseEntity<>("Missing data", HttpStatus.FORBIDDEN);
        }

        //Checking that accountFrom and accountTo are different
        if (fromAccountNumber.equalsIgnoreCase(toAccountNumber)) {
            return new ResponseEntity<>("Origin and destiny are equals", HttpStatus.FORBIDDEN);
        }

        //Retrieving client who is making the transaction
        Client clientOrigin = clientService.findByEmail(authentication.getName());

        Optional<Account> originAccountOptional = Optional.ofNullable(accountService.findByNumber(fromAccountNumber));
        Account originAccount;

        //Checking originAccount is valid
        if (originAccountOptional.isPresent()) {
            originAccount = originAccountOptional.get();
        } else {
            return new ResponseEntity<>("Invalid origin account", HttpStatus.FORBIDDEN);
        }

        //Checking if originAccount belongs to the authenticated user
        if(clientOrigin.getAccounts().stream().noneMatch(account -> account == originAccount)){
            return new ResponseEntity<>("Origin account does not belong to current user", HttpStatus.FORBIDDEN);
        };

        //Checking destinyAccount is valid
        Optional<Account> destinyAccountOptional = Optional.ofNullable(accountService.findByNumber(toAccountNumber));
        Account destinyAccount;
        if (destinyAccountOptional.isPresent()) {
            destinyAccount = destinyAccountOptional.get();
        } else {
            return new ResponseEntity<>("Invalid destiny account", HttpStatus.FORBIDDEN);
        }

        //Checking that origin account has sufficient founds
        if (originAccount.getBalance() < amount){
            return new ResponseEntity<>("Insufficient funds", HttpStatus.FORBIDDEN);
        }

        Transaction debit = new Transaction(TransactionType.DEBIT, -amount, description + " " + originAccount.getNumber(), LocalDateTime.now());
        Transaction credit = new Transaction(TransactionType.CREDIT, amount, description + " " + destinyAccount.getNumber(), LocalDateTime.now());

        originAccount.substractAmount(amount);
        originAccount.addTransaction(debit);

        destinyAccount.addAmount(amount);
        destinyAccount.addTransaction(credit);

        transactionService.saveTransaction(debit);
        transactionService.saveTransaction(credit);
        accountService.saveAccount(originAccount);
        accountService.saveAccount(destinyAccount);

        return new ResponseEntity<>("Transaction resolved", HttpStatus.CREATED);


    }
}
