package com.ejemplo.smartbus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ejemplo.smartbus.BuildConfig
import com.ejemplo.smartbus.data.driver.DriverRepository
import com.ejemplo.smartbus.data.driver.DriverRequirements
import com.ejemplo.smartbus.data.driver.DriverValidators
import com.ejemplo.smartbus.data.model.DriverProfile
import com.ejemplo.smartbus.ui.theme.SmartBusBackground
import com.ejemplo.smartbus.ui.theme.SmartBusBlue
import com.ejemplo.smartbus.ui.theme.SmartBusGreen
import com.ejemplo.smartbus.ui.theme.SmartBusRed
import com.ejemplo.smartbus.ui.theme.SmartBusText
import com.ejemplo.smartbus.ui.theme.SmartBusTextSecondary
import com.ejemplo.smartbus.ui.theme.SmartBusYellow
import kotlinx.coroutines.launch

/**
 * Alta de conductor, paso a paso.
 *
 * El paso 1 son los datos de la persona. Los pasos de documentos y de
 * vehiculo siguen en [DriverDocumentsScreen] y [DriverVehicleScreen].
 */
@Composable
fun DriverOnboardingScreen(
    accountId: String,
    repository: DriverRepository,
    existing: DriverProfile?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {

    val scope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf(existing?.fullName.orEmpty()) }
    var documentType by remember { mutableStateOf(existing?.documentType ?: "DNI") }
    var documentId by remember { mutableStateOf(existing?.documentId.orEmpty()) }
    var birthDate by remember { mutableStateOf(existing?.birthDate.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var licenseNumber by remember { mutableStateOf(existing?.licenseNumber.orEmpty()) }
    var licenseClass by remember { mutableStateOf(existing?.licenseClass.orEmpty()) }
    var licenseExpiry by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf(existing?.emergencyContactName.orEmpty()) }
    var emergencyPhone by remember {
        mutableStateOf(existing?.emergencyContactPhone.orEmpty())
    }

    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    /**
     * Verificacion del celular: un codigo de 6 digitos por cuenta. Sin
     * esto el conductor no puede ponerse online, aunque lo demas este
     * completo.
     */
    val verifiedPhone = remember(existing, phone) {
        existing?.phoneVerified == true && existing?.phone == phone.trim()
    }

    var lastSentCode by remember { mutableStateOf<String?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var phoneJustVerified by remember { mutableStateOf(false) }

    /**
     * El DNI peruano lleva un digito de control. Validarlo aqui evita
     * que el conductor escriba cualquier numero y que el sistema le
     * acepte un documento que no existe.
     */
    val documentValid = remember(documentId, documentType) {
        when (documentType.uppercase()) {
            "DNI" -> DriverValidators.isValidPeruvianDni(documentId)
            "PASAPORTE" -> documentId.trim().length >= 6
            else -> documentId.isNotBlank()
        }
    }

    val phoneValid = remember(phone) {
        DriverValidators.isValidPeruvianPhone(phone)
    }

    val birthValid = remember(birthDate) {
        birthDate.isBlank() || DriverValidators.isValidBirthDate(birthDate)
    }

    val emergencyValid = remember(emergencyPhone) {
        DriverValidators.isValidPeruvianPhone(emergencyPhone)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartBusBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        TextButton(onClick = onBack) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Conduce con SmartBus",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SmartBusText
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Necesitamos saber quien eres y que vehiculo manejas. " +
                "Tus datos se revisan antes de que puedas trabajar.",
            style = MaterialTheme.typography.bodyMedium,
            color = SmartBusTextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionTitle("Tus datos")

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Nombre completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Box(modifier = Modifier.width(120.dp)) {

                OutlinedTextField(
                    value = documentType,
                    onValueChange = { documentType = it.uppercase() },
                    label = { Text("Tipo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = documentId,
                onValueChange = { documentId = it.trim() },
                label = { Text("Numero de documento") },
                singleLine = true,
                isError = documentId.isNotBlank() && !documentValid,
                supportingText = if (documentId.isNotBlank() && !documentValid) {
                    {
                        Text(
                            if (documentType.uppercase() == "DNI") {
                                "El digito de control no cuadra."
                            } else {
                                "Documento demasiado corto."
                            }
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it },
            label = { Text("Fecha de nacimiento (AAAA-MM-DD)") },
            singleLine = true,
            isError = !birthValid,
            supportingText = if (!birthValid) {
                { Text("Debes ser mayor de 18 anos.") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() } },
            label = { Text("Celular (9 digitos)") },
            singleLine = true,
            isError = phone.isNotBlank() && !phoneValid,
            supportingText = {
                Text(
                    if (phoneValid) {
                        "Te enviaremos un codigo para confirmarlo."
                    } else {
                        "Formato: 9xxxxxxxx"
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        if (phone.isNotBlank() && phoneValid && !verifiedPhone && !phoneJustVerified) {

            PhoneVerificationCard(
                verifying = verifying,
                verified = verifiedPhone || phoneJustVerified,
                otpInput = otpInput,
                lastSentCode = lastSentCode,
                onOtpChange = {
                    otpInput = it.filter { c -> c.isDigit() }.take(6)
                },
                onRequestCode = {
                    scope.launch {
                        lastSentCode = repository.requestPhoneCode(accountId)
                    }
                },
                onVerify = {
                    scope.launch {
                        verifying = true
                        phoneJustVerified =
                            repository.verifyPhoneCode(accountId, otpInput)
                        verifying = false
                        if (phoneJustVerified) {
                            otpInput = ""
                            lastSentCode = null
                        }
                    }
                }
            )
        } else if ((verifiedPhone || phoneJustVerified) && phone.isNotBlank()) {

            PhoneVerifiedCard()
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionTitle("Tu licencia")

        OutlinedTextField(
            value = licenseNumber,
            onValueChange = { licenseNumber = it.trim() },
            label = { Text("Numero de licencia") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            OutlinedTextField(
                value = licenseClass,
                onValueChange = { licenseClass = it.uppercase() },
                label = { Text("Clase") },
                singleLine = true,
                placeholder = { Text("A-IIIb") },
                isError = licenseClass.isNotBlank() &&
                    !DriverValidators.isLicenseClassAllowedForPassengers(licenseClass),
                supportingText = {
                    Text(
                        if (DriverValidators.isLicenseClassAllowedForPassengers(licenseClass)) {
                            "Aceptada"
                        } else {
                            "Se requiere A-III"
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = licenseExpiry,
                onValueChange = { licenseExpiry = it },
                label = { Text("Vence (AAAA-MM-DD)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionTitle("Contacto de emergencia")

        Text(
            text = "Si no respondes durante un viaje, avisamos a esta persona.",
            style = MaterialTheme.typography.bodySmall,
            color = SmartBusTextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = emergencyName,
            onValueChange = { emergencyName = it },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = emergencyPhone,
            onValueChange = { emergencyPhone = it.filter { c -> c.isDigit() } },
            label = { Text("Celular") },
            singleLine = true,
            isError = emergencyPhone.isNotBlank() && !emergencyValid,
            supportingText = {
                Text(if (emergencyValid) "Correcto" else "Formato: 9xxxxxxxx")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = SmartBusRed.copy(alpha = 0.10f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = SmartBusRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {

                error = null

                when {

                    fullName.trim().split(" ").size < 2 ->
                        error = "Escribe tu nombre y apellido."

                    documentId.isBlank() ->
                        error = "Falta tu documento de identidad."

                    !documentValid ->
                        error = "El documento no es valido. Revisa el numero."

                    !birthValid ->
                        error = "La fecha de nacimiento no es valida o no " +
                            "cumples los 18 anos."

                    !phoneValid ->
                        error = "El celular debe tener 9 digitos y empezar por 9."

                    licenseNumber.isBlank() ->
                        error = "Falta el numero de licencia."

                    licenseClass.isNotBlank() &&
                        !DriverValidators.isLicenseClassAllowedForPassengers(licenseClass) ->
                        error = "Tu clase de licencia no permite transportar " +
                            "pasajeros. Se requiere A-III."

                    emergencyName.isBlank() || emergencyPhone.isBlank() ->
                        error = "Necesitamos un contacto de emergencia."

                    !emergencyValid ->
                        error = "El celular de emergencia debe tener 9 digitos."

                    !verifiedPhone && !phoneJustVerified ->
                        error = "Verifica tu celular con el codigo de 6 " +
                            "digitos que te enviamos por SMS."

                    else -> {

                        saving = true

                        scope.launch {

                            if (
                                repository.isDocumentAlreadyRegistered(
                                    documentId = documentId.trim(),
                                    exceptAccountId = accountId
                                )
                            ) {
                                error = "Ese documento ya esta registrado " +
                                    "con otra cuenta."
                                saving = false
                                return@launch
                            }

                            val expiryMillis = parseDate(licenseExpiry)

                            repository.saveProfile(
                                DriverProfile(
                                    accountId = accountId,
                                    fullName = fullName.trim(),
                                    documentId = documentId.trim(),
                                    documentType = documentType.trim().uppercase(),
                                    birthDate = birthDate.trim().ifBlank { null },
                                    phone = phone.trim(),
                                    licenseNumber = licenseNumber.trim(),
                                    licenseClass = licenseClass.trim().uppercase(),
                                    licenseExpiresAt = expiryMillis,
                                    emergencyContactName = emergencyName.trim(),
                                    emergencyContactPhone = emergencyPhone.trim(),
                                    phoneVerified = verifiedPhone || phoneJustVerified,
                                    status = existing?.status
                                        ?: com.ejemplo.smartbus.data.model
                                            .DriverVerificationStatus.NOT_STARTED,
                                    statusReason = existing?.statusReason.orEmpty(),
                                    rating = existing?.rating ?: 0f,
                                    totalTrips = existing?.totalTrips ?: 0,
                                    createdAt = existing?.createdAt
                                        ?: System.currentTimeMillis()
                                )
                            )

                            saving = false
                            onSaved()
                        }
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Guardar y continuar")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = SmartBusBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Que sigue despues",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartBusText
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                DriverRequirements.steps.drop(1).forEach { step ->
                    Text(
                        text = "- ${step.title}: ${step.items.size} documento(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartBusTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = SmartBusText,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

/**
 * Tarjeta para pedir el codigo y confirmarlo. En la version de prueba
 * el codigo se muestra aqui mismo porque todavia no hay SMS de verdad.
 */
@Composable
private fun PhoneVerificationCard(
    verifying: Boolean,
    verified: Boolean,
    otpInput: String,
    lastSentCode: String?,
    onOtpChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    onVerify: () -> Unit
) {

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = SmartBusBlue,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Verifica tu celular",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartBusText
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Para proteger tu cuenta, te enviamos un codigo de " +
                    "6 digitos. Sin confirmarlo no puedes ponerte en servicio.",
                style = MaterialTheme.typography.bodySmall,
                color = SmartBusTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (lastSentCode == null) {

                OutlinedButton(
                    onClick = onRequestCode,
                    enabled = !verifying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enviar codigo")
                }

            } else {

                OutlinedTextField(
                    value = otpInput,
                    onValueChange = onOtpChange,
                    label = { Text("Codigo de 6 digitos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = otpInput.length == 6 && !phoneCodeValid(otpInput, lastSentCode) && !verified,
                    supportingText = {
                        if (BuildConfig.DEBUG && lastSentCode != null) {
                            Text("Version de prueba. Tu codigo es: $lastSentCode")
                        } else {
                            Text("Vence en 5 minutos.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onVerify,
                    enabled = otpInput.length == 6 && !verifying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (verifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirmar codigo")
                    }
                }
            }
        }
    }
}

/**
 * Estado final: el numero ya fue confirmado.
 */
@Composable
private fun PhoneVerifiedCard() {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = SmartBusGreen.copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = SmartBusGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Celular verificado",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SmartBusGreen
            )
        }
    }
}

/**
 * Ayuda a mostrar un error si el codigo no coincide antes de
 * presionar Confirmar: compara el codigo guardado.
 */
private fun phoneCodeValid(input: String, sent: String?): Boolean =
    input.trim() == sent

/**
 * Convierte AAAA-MM-DD a milisegundos, o null si no se puede.
 */
internal fun parseDate(value: String): Long? {

    val clean = value.trim()

    if (clean.isEmpty()) {
        return null
    }

    val parts = clean.split("-")

    if (parts.size != 3) {
        return null
    }

    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null

    if (month !in 1..12 || day !in 1..31) {
        return null
    }

    return java.util.Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 23, 59, 59)
    }.timeInMillis
}