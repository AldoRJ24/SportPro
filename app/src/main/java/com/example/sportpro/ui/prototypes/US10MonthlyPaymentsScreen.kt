package com.example.sportpro.ui.prototypes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportpro.model.MonthlyPayment
import com.example.sportpro.model.PaymentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun US10MonthlyPaymentsScreen(
    onBack: () -> Unit = {}
) {
    var paymentsList by remember {
        mutableStateOf(
            listOf(
                MonthlyPayment("1", "Febrero 2026", "Lucas Silva (Sub-15)", "$45.000", "05/02/2026", PaymentStatus.PAID, paymentDate = "03/02/2026"),
                MonthlyPayment("2", "Marzo 2026", "Lucas Silva (Sub-15)", "$45.000", "05/03/2026", PaymentStatus.PENDING),
                MonthlyPayment("3", "Enero 2026", "Lucas Silva (Sub-15)", "$45.000", "05/01/2026", PaymentStatus.PAID, paymentDate = "05/01/2026")
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("US-10: Control de Mensualidades") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cartel de Simulación
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Registro Simulado de Cuotas", fontWeight = FontWeight.Bold)
                        Text(
                            "Control manual para el Administrador y consulta de estado de cuenta para Padres. No requiere dinero real ni pasarela transaccional.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                "Estado de Cuenta - Alumno: Lucas Silva",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(paymentsList) { payment ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (payment.status) {
                                PaymentStatus.PAID -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                PaymentStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                                PaymentStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(payment.monthYear, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(payment.amount, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }

                            Text("Vencimiento: ${payment.dueDate}", style = MaterialTheme.typography.bodySmall)

                            if (payment.paymentDate != null) {
                                Text("Fecha de Pago Registrada: ${payment.paymentDate}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Badge(
                                    containerColor = when (payment.status) {
                                        PaymentStatus.PAID -> Color(0xFF2E7D32)
                                        PaymentStatus.PENDING -> Color(0xFFFBC02D)
                                        PaymentStatus.OVERDUE -> Color(0xFFD32F2F)
                                    }
                                ) {
                                    Text(
                                        when (payment.status) {
                                            PaymentStatus.PAID -> "PAGADO (REGISTRADO MANUAL)"
                                            PaymentStatus.PENDING -> "PENDIENTE"
                                            PaymentStatus.OVERDUE -> "VENCIDO"
                                        },
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (payment.status == PaymentStatus.PENDING) {
                                    Button(
                                        onClick = {
                                            paymentsList = paymentsList.map {
                                                if (it.id == payment.id) it.copy(
                                                    status = PaymentStatus.PAID,
                                                    paymentDate = "24/02/2026"
                                                ) else it
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Marcar Pagado (Admin)", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun US10MonthlyPaymentsScreenPreview() {
    US10MonthlyPaymentsScreen()
}
