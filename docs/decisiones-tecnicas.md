# Decisiones técnicas

Cada decisión sigue el mismo formato: el problema, lo que se decidió, por qué, y lo que se sacrifica a cambio.

## 1. Las reglas viven en el servidor

**Problema.** Un APK se puede descompilar y modificar. Si la app decide quién tiene permiso, cualquiera con conocimientos puede saltarse la regla.

**Decisión.** Todo permiso y toda regla de negocio se valida en PostgreSQL con Row Level Security, funciones y triggers. La app repite algunas validaciones solo para mostrar un mensaje claro antes de enviar.

**Por qué.** El servidor es el único lugar que el usuario no controla. Además, la regla queda en un solo sitio y sirve igual si mañana existe una versión web.

**Costo.** Hay lógica en SQL, que es menos cómoda de escribir y de probar que Kotlin. Se compensa con la prueba de humo en SQL (`supabase/test/smoke_test.sql`).

## 2. Escribir por funciones, no por tablas

**Problema.** Dar permiso de escritura sobre una tabla deja abiertas todas las combinaciones de columnas. Por ejemplo, permitir «actualizar un pago» permitiría también marcarlo como confirmado.

**Decisión.** Las tablas sensibles no aceptan escrituras directas. La app llama a funciones `SECURITY DEFINER` (por ejemplo `fee_report_payment`) que comprueban quién llama, validan los datos, escriben, avisan y registran en auditoría.

**Por qué.** Cada función es una operación de negocio completa: no se puede hacer «a medias» ni saltarse un paso.

**Costo.** Más funciones que mantener. Toda función con permisos elevados fija su `search_path` y revoca el acceso al rol anónimo; la auditoría automática lo comprueba.

## 3. Lo calculado no se guarda

**Problema.** Si se guarda el marcador y aparte los goles, tarde o temprano dejan de coincidir.

**Decisión.** Se guarda solo el dato base y lo demás se calcula:

| Se calcula | A partir de |
|---|---|
| Marcador | Eventos de gol vigentes |
| Estadísticas | Eventos vigentes de partidos finalizados |
| Minuto del partido | Hora de inicio del periodo y segundos acumulados |
| Jugadores en cancha | Titulares, cambios y rojas |
| Cobro «vencido» | Estado pendiente y fecha de vencimiento |
| Documento «vencido» | Fecha de vencimiento |

**Por qué.** Corregir o anular un evento arregla todo lo demás sin tocar nada más. Tampoco hace falta un proceso nocturno que cambie estados.

**Costo.** Cada lectura hace un poco más de trabajo. A la escala de una academia no se nota.

## 4. Nada se borra

**Problema.** Con menores y con dinero de por medio, hay que poder responder «quién cambió esto y por qué».

**Decisión.** No existe el borrado. Los eventos se anulan, los pagos se rechazan o anulan, los cobros se exoneran, los jugadores se dan de baja y los documentos se reemplazan. Cada una de esas acciones exige un motivo y se escribe en `audit_log`.

**Por qué.** La trazabilidad protege a la academia y a las familias ante un reclamo.

**Costo.** Las tablas crecen y las consultas deben filtrar por estado. La auditoría comprueba que la app no tenga permiso de borrado sobre jugadores, plantel, partidos ni eventos.

## 5. Idempotencia con `client_event_id`

**Problema.** En la cancha la señal se cae. Si un gol se envía, llega al servidor y la respuesta se pierde, el reintento lo duplicaría.

**Decisión.** Cada evento lleva un identificador creado en el celular y la tabla tiene una restricción única sobre (partido, identificador). Un reintento recibe el error «duplicado» y la app lo trata como éxito.

**Por qué.** Es la forma más simple de que reintentar sea siempre seguro, sin coordinación extra.

**Costo.** Ninguno relevante. La misma idea se usa en los cobros mensuales: la clave única (equipo, jugador, mes) permite pulsar «Generar cobros» varias veces sin duplicar.

## 6. Reloj derivado de datos del servidor

**Problema.** Si cada celular lleva su propio cronómetro, dos personas ven minutos distintos.

**Decisión.** El servidor guarda cuándo empezó el periodo y cuántos segundos se habían jugado. Cada celular calcula el minuto con eso.

**Por qué.** Todos ven lo mismo, incluso quien abre la app a mitad del partido.

**Costo.** Si la hora del celular está muy desajustada, su minuto se desvía. El minuto que queda guardado en cada evento es el que eligió quien lo registró, así que la cronología no se ve afectada.

## 7. Privacidad de menores por diseño

**Problema.** La app maneja nombres, fotos, datos de salud y documentos de menores.

**Decisión.**

- Un menor aparece como «Nombre I.» para quien no es su apoderado ni su staff. Lo resuelve una función del servidor, no la pantalla.
- Los menores no pueden publicar foto.
- Los datos sensibles están en una tabla aparte con permisos más estrictos.
- Los documentos van a un bucket privado y se abren con URL firmada de 5 minutos.
- La IA recibe seudónimos; el servidor los reemplaza por nombres después.
- Un jugador de 13 a 17 años no entra a la app hasta que su apoderado aprueba el vínculo.

**Por qué.** Que la privacidad dependa del servidor impide que un error en una pantalla exponga datos.

**Costo.** Algunas pantallas muestran menos información de la que sería cómoda. Por ejemplo, quien revisa las inscripciones a una prueba no ve el apellido.

## 8. La clave de la IA nunca sale del servidor

**Problema.** Una clave dentro del APK se puede extraer.

**Decisión.** La app llama a una Edge Function. La función valida al usuario, arma los datos, llama a Gemini con la clave guardada como secreto y devuelve solo el borrador.

**Por qué.** El APK lleva únicamente la clave publicable de Supabase, que está pensada para ser pública y no da acceso por sí sola: lo que protege los datos es RLS.

**Costo.** Una pieza más que desplegar y configurar.

## 9. MVVM con contenedor manual

**Problema.** Organizar la app sin sumar complejidad innecesaria para un proyecto de este tamaño.

**Decisión.** Pantalla → ViewModel con `StateFlow` → repositorio. Las dependencias se crean en una clase simple, `AppContainer`, y se entregan con un `CompositionLocal`. No se usa un framework de inyección de dependencias.

**Por qué.** Es explícito y fácil de seguir: se puede leer de arriba abajo cómo se construye cada cosa.

**Costo.** En una app más grande habría que escribir más código de conexión a mano.

## 10. Lógica pura separada de Android

**Problema.** Los tests que necesitan emulador son lentos y frágiles.

**Decisión.** El reloj, las formaciones, los jugadores en cancha y las validaciones de formularios son funciones Kotlin sin dependencias de Android. Viven en `core/data/` y se prueban con JUnit en la PC.

**Por qué.** Los 128 tests corren en segundos y sin celular.

**Costo.** Hubo que sacar alguna validación del ViewModel hacia el modelo del formulario.

## 11. Textos en `strings.xml`, también fuera de Compose

**Problema.** Los ViewModels y las utilidades de formato no pueden usar `stringResource`, y pasarles un `Context` complica los tests.

**Decisión.** Las pantallas usan `stringResource`. El código que no es Compose usa `AppText`, un acceso único a los recursos que se instala al arrancar la app y que en los tests se reemplaza por una versión falsa.

**Por qué.** Todos los textos quedan en un solo archivo, con plurales reales, y la lógica sigue siendo probable.

**Costo.** `AppText` es un objeto global. Se aceptó porque solo lee recursos y no guarda estado de la app.

## 12. Notificaciones dentro de la app, filtradas en el servidor

**Problema.** Avisar de convocatorias, pagos y solicitudes, respetando lo que cada persona quiere recibir.

**Decisión.** Todas las notificaciones pasan por una sola función, `_notify`, que consulta las preferencias del usuario antes de crear el aviso. Llegan a la app por Realtime.

**Por qué.** Al estar el filtro en un único punto, vale para los avisos existentes y para los que se agreguen después.

**Costo.** No hay notificaciones con la app cerrada. Agregarlas requeriría un servicio de mensajería push y está fuera del alcance actual.

## 13. Cargador de imágenes propio

**Problema.** La biblioteca de imágenes más habitual exigía una versión de Compose más nueva que la del proyecto.

**Decisión.** Se escribió un cargador pequeño (`ui/components/Avatar.kt`) que descarga, reduce y muestra la imagen, con iniciales como respaldo.

**Por qué.** Evita cambiar versiones de bibliotecas a mitad del proyecto por una sola función.

**Costo.** No tiene caché en disco ni las optimizaciones de una biblioteca dedicada.

## 14. Migraciones repetibles y probadas en local

**Problema.** Las migraciones se ejecutan a mano en el SQL Editor; un error a mitad dejaría la base a medias.

**Decisión.** Cada migración es transaccional y, desde la 002, repetible. Nunca se edita una migración ya aplicada: se crea otra. Antes de aplicarlas en Supabase se prueban en un Postgres desechable con `supabase/test/run_local.sh`.

**Por qué.** Se detectan errores sin tocar los datos reales. El script aplica todo dos veces para comprobar que repetir no rompe nada.

**Costo.** El entorno local imita solo lo mínimo de Supabase (roles, `auth.uid()` y tablas de Storage). No reemplaza la prueba final en el proyecto real.

## 15. La lista de partidos la decide el servidor

**Problema.** La pestaña «Partidos» mostraba todo lo que el usuario tenía permiso de ver, incluidos partidos públicos de academias que no le interesan.

**Decisión.** La función `my_matches` devuelve solo los partidos del usuario y el motivo de cada uno: sigue al equipo o a la academia, juega su hijo, dirige el equipo, juega él o lo marcó como favorito. La app trae esos partidos y aplica en memoria el buscador y los filtros.

**Por qué.** «Poder ver» y «querer ver» son cosas distintas. El permiso sigue en las políticas de fila; la relevancia se calcula en un único lugar del servidor y vale igual para cualquier cliente.

**Costo.** Dos consultas en lugar de una al abrir la pestaña. Los filtros no vuelven a consultar: trabajan sobre la lista ya descargada (máximo 100 partidos).

## 16. Tema claro en azul, blanco y negro

**Problema.** La app se usa al aire libre y debe verse sobria.

**Decisión.** Un solo tema claro: fondo blanco, texto negro y un único azul de marca para la acción principal y lo seleccionado. El verde, el ámbar y el rojo se reservan para estados (pagado, pendiente, error, en vivo).

**Por qué.** Un fondo blanco con texto negro se lee mejor bajo el sol que un tema oscuro, y un solo color de marca mantiene la pantalla limpia.

**Costo.** No hay modo oscuro. Los colores viven en `ui/theme/Color.kt`; agregarlo exigiría definir una segunda paleta.

## 17. Un partido entre academias necesita el sí de ambos entrenadores

**Problema.** Un DT podía crear un partido contra el equipo de otra academia sin que su entrenador se enterara ni lo aceptara.

**Decisión.** Contra el equipo de otro entrenador se envía un desafío (`match_challenges`). El partido solo se crea cuando el desafiado acepta, y lo crea el servidor con los ajustes propuestos. La política de inserción de `matches` ya no permite crear directo un partido contra un equipo que uno no gestiona. La regla de división (edad máxima a 1 de distancia) se valida en el servidor; la app solo muestra los equipos apagados con el motivo.

**Por qué.** El acuerdo entre dos entrenadores es una regla de negocio: si viviera solo en la app, otra versión de la app podría saltársela.

**Costo.** Un paso más antes de tener el partido. A cambio, el rival nunca encuentra en su lista un partido que no aceptó.

## 18. El árbitro registra sin cuenta, con un código por partido

**Problema.** El árbitro casi nunca es miembro de la academia y no va a crear una cuenta para un solo partido.

**Decisión.** El DT genera un QR con un código secreto de un solo partido. El QR abre una página web publicada en Vercel que llama a cuatro funciones del servidor; cada una valida el código y luego aplica las mismas reglas que la app. Para no duplicar lógica, el cambio de estado del partido se separó en una función interna (`_match_apply_state`) que usan tanto la app como la página.

**Por qué.** Es la forma más simple para el árbitro (escanear y empezar) sin abrir las tablas al público: el rol anónimo sigue sin permisos sobre ninguna tabla.

**Costo.** Quien obtenga el QR puede registrar eventos de ese partido. Se mitiga con el vencimiento, el botón para cambiar o desactivar el código y la auditoría. La página consulta el partido cada 4 segundos en lugar de usar Realtime, porque Realtime filtra por permisos de usuario y el árbitro no tiene sesión.

## 19. Alineaciones secretas hasta 1 hora antes, y completas siempre

**Problema.** En un partido entre dos entrenadores, ninguno debería ver la alineación del otro antes de tiempo, y un partido no puede quedar sin jugar porque alguien olvidó armarla.

**Decisión.** Cada entrenador arma y envía solo su lado; las políticas de fila y `get_match_lineup` ocultan el lado ajeno hasta la confirmación. A 1 hora del partido el servidor cierra las dos: si una no está completa, la autocompleta (banca, confirmados, resto del plantel, nunca quien dijo «no asiste», siempre con 1 arquero). Recién ahí avisa a familias y seguidores, el partido aparece en sus listas y se puede generar el QR del árbitro.

**Por qué.** La hora límite es una regla de negocio: la aplica el servidor con una tarea programada (pg_cron) y, como respaldo, al abrir el partido. Si la regla viviera en la app, bastaría con no abrirla para saltársela.

**Costo.** El autocompletado puede alinear a un jugador que no confirmó (lo pidió el usuario: «se pondrá una predeterminada»). Queda marcado como completado por el sistema y su entrenador recibe un aviso. Sin pg_cron, los avisos llegan recién cuando alguien abre el partido.

## 20. Alineación en la cancha: lugares relativos a la mitad propia

**Problema.** El entrenador quiere elegir una formación, ver a sus jugadores acomodados en la cancha y mover a alguno (por ejemplo, adelantar a un lateral). El público debe ver lo mismo en el partido en vivo, con el local arriba y el visitante abajo.

**Decisión.** Cada titular guarda su lugar (`slot_x`, `slot_y`) en coordenadas de 0 a 1 dentro de su propia mitad, como si su equipo atacara hacia arriba. La app gira la mitad del local al dibujarla. La posición (arquero, defensa, medio, delantero) sigue siendo la de la casilla aunque se arrastre, así las reglas del servidor (1 arquero, arquero automático en atajadas) no cambian. Guardar la alineación pasa a ser una sola función (`lineup_save`) que reemplaza todo en una transacción. Las formaciones propias del entrenador se guardan aparte y solo las ve él.

**Por qué.** Un mismo sistema de coordenadas sirve para los dos lados y para cualquier tamaño de pantalla. Antes la app borraba e insertaba en dos pedidos: si el segundo fallaba, la alineación quedaba vacía. La foto del jugador solo sale si es mayor de edad o si quien mira es su apoderado, él mismo o su staff (`_player_photo`).

**Costo.** Se creó `get_match_lineup_pitch` en lugar de cambiar `get_match_lineup`: cambiar el tipo de resultado obligaba a borrarla, y las migraciones 002 y 017 ya no podrían volver a ejecutarse. Las alineaciones viejas y las autocompletadas no tienen lugares: la app las acomoda según la formación.

## Antecedente

`DISEÑO_TECNICO_W7.md`, en la raíz, es el diseño preliminar de la semana 7. Dos cosas cambiaron respecto a ese documento: la IA ya no recibe nombres de jugadores, sino seudónimos, y la llamada a la IA la hace el servidor en lugar de la app.
