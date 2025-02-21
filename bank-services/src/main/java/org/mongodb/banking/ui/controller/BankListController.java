package org.mongodb.banking.ui.controller;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.mongodb.banking.client.BankingApiClient;
import org.mongodb.banking.ui.model.BankDetailModel;
import org.mongodb.banking.ui.model.BankListModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.util.*;

public class BankListController {

    private static final Logger logger = LoggerFactory.getLogger(BankListController.class);

    // how many milliseconds should elapse between refreshing data shown in the UI
    private static final int REFRESH_INTERVAL_MILLIS = 2000;

    private final BankListModel model;
    private final BankingApiClient bankClient;
    private final Map<String, BankDetailController> detailControllers;

    public BankListController(BankingApiClient bankClient) {
        this.bankClient = bankClient;
        detailControllers = Collections.synchronizedMap(new HashMap<>());

        model = new BankListModel();
        refresh();

        model.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getNewValue() != null) {
                // add new account
                String bankName = (String) evt.getNewValue();

                bankClient.createBank(bankName, 1000); // arbitrarily selected default initial balance

                BankDetailController detailController = new BankDetailController(bankName, bankClient);
                detailControllers.put(bankName, detailController);
                model.addBankName(bankName);
            }
        });

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refresh();
            }
        }, 0, REFRESH_INTERVAL_MILLIS);
    }

    public BankDetailModel getBankDetailModel(String bankName) {
        return detailControllers.get(bankName).getModel();
    }

    private void refresh() {
        logger.trace("BankListController is refreshing model");
        List<String> latest = bankClient.getAllBankNames();
        List<String> modelNames = model.getBankNames();

        List<String> namesToAdd = new ArrayList<>();
        for (String name : latest) {
            if (!modelNames.contains(name)) {
                namesToAdd.add(name);
            }
        }

        List<String> namesToRemove = new ArrayList<>();
        for (String name : modelNames) {
            if (!latest.contains(name)) {
                namesToRemove.add(name);
            }
        }

        logger.trace("BankListController found {} new items to add", namesToAdd.size());
        for (String name : namesToAdd) {
            BankDetailController detailController = new BankDetailController(name, bankClient);
            detailControllers.put(name, detailController);
            model.addBankName(name);
        }

        logger.trace("BankListController found {} old items to remove", namesToRemove.size());
        for (String name : namesToRemove) {
            detailControllers.remove(name);
            model.removeBankName(name);
        }

        for (String name : latest) {
            if (!namesToAdd.contains(name) && !namesToRemove.contains(name)) {
                // this was there before, update it
                BankDetailController detailController = detailControllers.get(name);
                if (detailController != null) {
                    detailController.refresh();
                }
            }
        }
    }

    public BankListModel getModel() {
        return model;
    }

    public void approvePendingTransfer(String workflowId, String managerName) {
        if (workflowId == null || workflowId.isEmpty()
                || managerName == null || managerName.length() == 0) {
            return;
        }

        // The next several lines are similar to what's in the class used to start
        // the Workflow Execution, although this uses an untyped stub because 
        // the code for that Workflow is in a separate project. Sending a Signal to 
        // a Workflow Execution involves sending a request to the Temporal Service,
        // much like happens when you start the Workflow Execution. We must specify the
        // Workflow ID corresponding to the Workflow Execution we want to Signal (i.e.,
        // the one that was launched in the Starter class).
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        client.newUntypedWorkflowStub(workflowId).signal("approve", managerName);
    }
}
