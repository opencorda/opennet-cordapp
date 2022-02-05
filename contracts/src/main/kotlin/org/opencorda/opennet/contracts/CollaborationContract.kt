package org.opencorda.opennet.contracts

import net.corda.v5.ledger.contracts.CommandData
import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.contracts.requireThat
import net.corda.v5.ledger.transactions.LedgerTransaction
import net.corda.v5.ledger.transactions.outputsOfType
import org.opencorda.opennet.states.Collaboration

class CollaborationContract: Contract {

    // Used to indicate the intent.
    interface Commands : CommandData {
        class Create : Commands
        class AddOperation: Commands
    }

    companion object {
        // This is used to identify our contract when building a transaction.
        const val ID = "org.opencorda.opennet.contracts.CollaborationContract"
    }

    override fun verify(tx: LedgerTransaction) {

        //Extract the command from the transaction.
        val commandData = tx.commands[0].value

        when(commandData) {

            is Commands.Create -> requireThat {
                val output = tx.outputsOfType(Collaboration::class.java)[0]
                "There should only ever be a single output of a collaboration".using(tx.outputs.size == 1)
                "The Collaboration must have a description".using(tx.outputsOfType(Collaboration::class.java)[0].description != "")
                null
            }

            is Commands.AddOperation -> requireThat {
                val output = tx.outputsOfType(Collaboration::class.java)[0]
                "There should only ever be a single output of a collaboration".using(tx.outputs.size == 1)
                null
            }




        }



    }
}