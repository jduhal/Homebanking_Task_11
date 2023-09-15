package com.mindhub.homebanking.controllers;

import com.mindhub.homebanking.dtos.LoanApplicationDTO;
import com.mindhub.homebanking.dtos.LoanDTO;
import com.mindhub.homebanking.models.*;
import com.mindhub.homebanking.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class LoanController {

    @Autowired
    private LoanService loanService;

    @Autowired
    private ClientLoanService clientLoanService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ClientService clientService;

    public LoanController() {
    }


    @GetMapping("/loans")
    public List<LoanDTO> getLoans() {
        return loanService.getLoansDTO();
    }

    @Transactional
    @PostMapping(path = "/loans")
    public ResponseEntity<Object> createLoan(@RequestBody LoanApplicationDTO loanApplicationDTO,
                                             Authentication auth) {

        //Checking parameters validity
        if(loanApplicationDTO.getPayments() <= 0 || loanApplicationDTO.getToAccountNumber().isEmpty() || loanApplicationDTO.getAmount() <= 0 ) {
            return new ResponseEntity<>("Invalid data", HttpStatus.FORBIDDEN);
        }

        List<Loan> listOfLoans = loanService.getLoans();

        //Checking if applied loan is a valid loan
        if (listOfLoans.stream().noneMatch(loan -> loan.getId() == loanApplicationDTO.getLoanId())) {
            return new ResponseEntity<>("There is no such type of loan", HttpStatus.FORBIDDEN);
        }

        //Retrieving the loan on its type
        Loan loanSelected = loanService.findById(loanApplicationDTO.getLoanId());

        //Checking amount validity
        if ( loanApplicationDTO.getAmount() > loanSelected.getMaxAmount()){
            return new ResponseEntity<>("Cannot exceed loan maximum amount", HttpStatus.FORBIDDEN);
        }

        //Checking payments validity
        if (loanSelected.getPayments().stream().noneMatch( loanPayments -> loanPayments == loanApplicationDTO.getPayments())) {
            return new ResponseEntity<>("Must select a valid number of payments", HttpStatus.FORBIDDEN);
        }

        //Checking if destiny account exists
        Optional<Account> destinyAccountOptional = Optional.ofNullable(accountService.findByNumber(loanApplicationDTO.getToAccountNumber()));
        Account destinyAccount;
        if (destinyAccountOptional.isPresent()) {
            destinyAccount = destinyAccountOptional.get();
        } else {
            return new ResponseEntity<>("Destiny account is not valid", HttpStatus.FORBIDDEN);
        }

        //Retrieving actual client
        Client actualClient = clientService.findByEmail(auth.getName());

        //Checking is destiny account belongs to the actual client
        if(actualClient.getAccounts().stream().noneMatch(account -> account == destinyAccount)){
            return new ResponseEntity<>("Origin account does not belong to current user", HttpStatus.FORBIDDEN);
        };


        //double interest = 1 + 0.1 * loanApplicationoDTO.getPayments() / 6;
        double interest = 1.2;


        ClientLoan requestedLoan = new ClientLoan((int)(loanApplicationDTO.getAmount() * interest), loanApplicationDTO.getPayments());
        Transaction creditLoanTransaction = new Transaction(TransactionType.CREDIT, loanApplicationDTO.getAmount(), loanSelected.getName() + " - loan approved", LocalDateTime.now());
        destinyAccount.addAmount(loanApplicationDTO.getAmount());
        destinyAccount.addTransaction(creditLoanTransaction);

        actualClient.addClientLoan(requestedLoan);
        loanSelected.addClientLoan(requestedLoan);
        clientLoanService.saveClientLoan(requestedLoan);
        transactionService.saveTransaction(creditLoanTransaction);
        loanService.saveLoan(loanSelected);
        clientService.saveClient(actualClient);



        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
