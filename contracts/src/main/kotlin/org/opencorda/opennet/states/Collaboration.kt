package org.opencorda.opennet.states

import com.google.gson.Gson
import org.opencorda.opennet.contracts.CollaborationContract
import net.corda.v5.application.identity.AbstractParty
import net.corda.v5.application.identity.Party
import net.corda.v5.application.utilities.JsonRepresentable
import net.corda.v5.ledger.UniqueIdentifier
import net.corda.v5.ledger.contracts.BelongsToContract
import net.corda.v5.ledger.contracts.LinearState

@BelongsToContract(CollaborationContract::class)
data class Collaboration (
    val collaborationId: String,
    val description: String,
    val originatingParty: Party,
    val members: List<Party>,
    val operations: List<CollaborationDataUpdate>,
    override val linearId: UniqueIdentifier,//LinearState required variable
) : LinearState, JsonRepresentable {

    override val participants: List<AbstractParty>
        get() {
            val parts = mutableListOf<AbstractParty>(originatingParty)
            parts.addAll(members)
            return parts.distinct()
        }

    override fun toJsonString(): String {
        return Gson().toJson(this.toDto())
    }

    fun toDto(): CollaborationDto {

        val members = members.map {
            it.name.toString()
        }

        val ops = operations.map {
            it.toDto()
        }

        return CollaborationDto(
            collaborationId = collaborationId,
            description = description,
            originatingParty = originatingParty.name.toString(),
            members = members,
            operations = ops,
            linearId = linearId.toString()
        )
    }

    fun addCollaborationUpdate(dataUpdate: CollaborationDataUpdate): Collaboration {
        val ops = operations.toMutableList()
        ops.add(dataUpdate)
        return Collaboration(
            collaborationId = collaborationId,
            description = description,
            originatingParty = originatingParty,
            members = members,
            operations = ops.distinct(),
            linearId = linearId
        )
    }


}



data class CollaborationDataUpdate (
    val updateId: String,
    val collaborationId: String,
    val updater: Party,
    val operation: String,
    val operationData: ByteArray
) {

    fun toDto() : CollaborationDataUpdateDto {
        return CollaborationDataUpdateDto(
            updateId = updateId,
            collaborationId = collaborationId,
            updater = updater.name.toString(),
            operation = operation,
            operationData = operationData
        )
    }


}

data class CollaborationDto (
    val collaborationId: String,
    val description: String,
    val originatingParty: String,
    val members: List<String>,
    val operations: List<CollaborationDataUpdateDto>,
    val linearId: String
)

data class CollaborationDataUpdateDto (
    val updateId: String,
    val collaborationId: String,
    val updater: String,
    val operation: String,
    val operationData: ByteArray
)
