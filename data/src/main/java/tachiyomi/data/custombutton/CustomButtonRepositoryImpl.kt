package tachiyomi.data.custombutton

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import tachiyomi.domain.custombuttons.model.CustomButton
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository

class CustomButtonRepositoryImpl : CustomButtonRepository {
    override fun subscribeAll(): Flow<List<CustomButton>> = flowOf(emptyList())

    override suspend fun getAll(): List<CustomButton> = emptyList()

    override suspend fun insertCustomButton(
        name: String,
        sortIndex: Long,
        content: String,
        longPressContent: String,
        onStartup: String,
    ) {
    }

    override suspend fun updatePartialCustomButton(update: CustomButtonUpdate) {
    }

    override suspend fun updatePartialCustomButtons(updates: List<CustomButtonUpdate>) {
    }

    override suspend fun deleteCustomButton(customButtonId: Long) {
    }
}
