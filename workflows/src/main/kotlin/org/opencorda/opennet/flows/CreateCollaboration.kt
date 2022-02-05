package org.opencorda.opennet.flows

import net.corda.systemflows.CollectSignaturesFlow
import net.corda.systemflows.FinalityFlow
import net.corda.v5.application.flows.*
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.flows.flowservices.FlowIdentity
import net.corda.v5.application.flows.flowservices.FlowMessaging
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.IdentityService
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.ledger.UniqueIdentifier
import net.corda.v5.ledger.contracts.Command
import net.corda.v5.ledger.services.NotaryLookupService
import net.corda.v5.ledger.transactions.SignedTransactionDigest
import net.corda.v5.ledger.transactions.TransactionBuilderFactory
import org.opencorda.opennet.contracts.CollaborationContract
import org.opencorda.opennet.states.Collaboration
import org.opencorda.opennet.states.CollaborationDataUpdate
import java.security.PublicKey
import java.util.*
import kotlin.NoSuchElementException

@InitiatingFlow
@StartableByRPC
class CreateCollaboration @JsonConstructor constructor(private val params: RpcStartFlowRequestParameters) : Flow<SignedTransactionDigest> {

    @CordaInject
    lateinit var flowEngine: FlowEngine
    @CordaInject
    lateinit var flowIdentity: FlowIdentity
    @CordaInject
    lateinit var flowMessaging: FlowMessaging
    @CordaInject
    lateinit var transactionBuilderFactory: TransactionBuilderFactory
    @CordaInject
    lateinit var identityService: IdentityService
    @CordaInject
    lateinit var notaryLookup: NotaryLookupService
    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    override fun call(): SignedTransactionDigest {

        // Parse Parameters
        val mapOfParams: Map<String, String> = jsonMarshallingService.parseJson(params.parametersInJson)
        val collabDescription = with(mapOfParams["collabDescription"] ?: throw BadRpcStartFlowRequestException("Collaboration State Parameter \"collabDescription\" missing.")) {
            this
        }
        val unparsedParticipants = with(mapOfParams["participants"] ?: throw BadRpcStartFlowRequestException("Collaboration State Parameter \"participants\" missing.")) {
            this
        }

        // Parse out the participants
        val participantList = mutableListOf<Party>()
        if(unparsedParticipants.indexOf("^") >= 0) {
            val parts = unparsedParticipants.split("^")
            parts.forEach {
                val parsedName = CordaX500Name.parse(it)
                val participantParty = identityService.partyFromName(parsedName) ?: throw NoSuchElementException("No party found for X500 name $parsedName")
                participantList.add(participantParty)
            }
        }

        // Find our notary
        val notary = notaryLookup.getNotary(CordaX500Name.parse("C=US, L=Seattle, O=Notary"))!!

        // Build the output state
        val uniqueId = UniqueIdentifier()
        val newCollab = Collaboration(
            collaborationId = UUID.randomUUID().toString(),
            description = collabDescription,
            originatingParty = flowIdentity.ourIdentity,
            members = participantList,
            operations = listOf<CollaborationDataUpdate>(),
            linearId = uniqueId
        )


        // Build Transaction
        val signers = mutableListOf<PublicKey>(flowIdentity.ourIdentity.owningKey)
        participantList.forEach {
            signers.add(it.owningKey)
        }
        val txCommand = Command(CollaborationContract.Commands.Create(), signers.distinct())
        val txBuilder = transactionBuilderFactory.create()
            .setNotary(notary)
            .addOutputState(newCollab, CollaborationContract.ID)
            .addCommand(txCommand)

        // Verify this transaction is valid
        txBuilder.verify()

        // Sign the TX
        val partSignedTx = txBuilder.sign()

        // Send to the other participants
        val sessions = mutableListOf<FlowSession>()
        participantList.forEach {
            val session = flowMessaging.initiateFlow(it)
            sessions.add(session)
        }
        val fullySignedTx = flowEngine.subFlow(CollectSignaturesFlow(partSignedTx, sessions))

        // Notarise and record the transaction in both parties' vaults.
        val notarisedTx = flowEngine.subFlow(FinalityFlow(fullySignedTx, sessions))

        return SignedTransactionDigest(
            notarisedTx.id,
            notarisedTx.tx.outputStates.map { output -> jsonMarshallingService.formatJson(output) },
            notarisedTx.sigs
        )





    }


}
