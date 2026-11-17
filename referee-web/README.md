# SportPro Árbitro (página web)

Página donde el árbitro registra los eventos de un partido **sin crear cuenta**. El DT le muestra un QR desde la app; el árbitro lo escanea con la cámara y se abre esta página. Lo que registra aparece al instante en la app de todos.

## Qué hay en esta carpeta

| Archivo | Para qué sirve |
|---|---|
| `index.html` | La pantalla |
| `styles.css` | Colores y diseño (azul, blanco y negro, igual que la app) |
| `app.js` | La lógica: reloj, registro en 3 toques, cola sin conexión, deshacer y anular |
| `api/config.js` | Función de Vercel que entrega la URL de Supabase y la clave publicable |
| `vercel.json` | Cabeceras de seguridad (no indexar, no incrustar, solo conectar con Supabase) |

No hay que instalar nada ni compilar: son archivos estáticos más una función pequeña.

## Antes de publicar

En Supabase → **SQL Editor**, ejecuta la migración `supabase/migrations/20261117120000_referee_links.sql`. Sin ella la página dirá que el código no es válido.

## Publicar en Vercel

Vercel no acepta arrastrar una carpeta: se publica desde GitHub o con su programa de consola. Elige una de las dos opciones.

### Opción A: desde GitHub

1. Sube el proyecto a GitHub (esta carpeta está dentro del repositorio).
2. En vercel.com → **Add New… → Project** → elige el repositorio.
3. En **Root Directory** elige `referee-web`.
4. En **Framework Preset** elige **Other**. Deja vacíos el comando de build y la carpeta de salida.
5. Antes de pulsar **Deploy**, abre **Environment Variables** y agrega las dos variables del paso siguiente.

### Opción B: con la consola

1. Abre una terminal dentro de esta carpeta (`referee-web`).
2. Ejecuta `npx vercel` y responde las preguntas. Cuando pregunte por el directorio, deja `./`.
3. Agrega las dos variables del paso siguiente en vercel.com → tu proyecto → **Settings → Environment Variables**.
4. Ejecuta `npx vercel --prod` para publicar la versión final.

### Variables de entorno (las configuras tú en Vercel)

| Nombre | Valor |
|---|---|
| `SUPABASE_URL` | La URL del proyecto. Supabase → **Project Settings → API** |
| `SUPABASE_PUBLISHABLE_KEY` | La clave **publicable** (empieza con `sb_publishable_`), en la misma pantalla |

- **Nunca** pongas la clave secreta (`sb_secret_…`) ni `service_role`. Si por error lo haces, `api/config.js` no la entrega y la página dirá que falta configuración.
- Después de agregar o cambiar variables, vuelve a publicar: **Deployments → … → Redeploy**.

## Conectar la app con la página

1. Copia la dirección que te dio Vercel (por ejemplo `https://sportpro-arbitro.vercel.app`).
2. En `local.properties` del proyecto Android agrega esta línea:

   ```
   REFEREE_WEB_URL=https://sportpro-arbitro.vercel.app
   ```

3. En Android Studio: **Sync** y **Rebuild**.

Sin esa línea, la app muestra el aviso «Falta la dirección de la página del árbitro» en lugar del QR.

## Cómo se usa

1. En la app, el DT del equipo local abre el partido y pulsa **QR del árbitro**.
2. El árbitro escanea el QR con la cámara. Se abre `https://tu-pagina.vercel.app/#CODIGO`.
3. **Iniciar partido**. El servidor exige las dos alineaciones completas, igual que en la app.
4. Para registrar un evento: tipo → equipo → jugador. El minuto sale del reloj y se puede ajustar con − y +.
   - **Penal:** pregunta si fue convertido, atajado o fallado.
   - **Cambio:** primero quién sale y después quién entra.
   - **Atajada:** se asigna sola al arquero en cancha.
   - **Gol en contra:** se marca antes de elegir al jugador.
5. **Deshacer** aparece unos segundos después de registrar. Para anular un evento anterior, tócalo en la cronología y escribe el motivo.
6. Entretiempo, segundo tiempo y final con los botones de arriba.

Si se cae la señal, los eventos quedan como **PENDIENTE** en el navegador y se envían solos al volver la conexión, sin duplicarse.

## Seguridad

- El código del QR tiene 64 caracteres al azar y solo sirve para ese partido.
- El código va después de `#`: esa parte del enlace no se envía al servidor de la página ni a otras páginas.
- El DT puede generar un código nuevo (el anterior deja de servir al instante) o desactivarlo.
- El código vence 3 horas después de terminar el partido.
- La página no lee tablas: solo llama a cuatro funciones del servidor que validan el código y todas las reglas del partido.
- Los menores aparecen como «Nombre I.» y no se muestran fotos.
- Cada acción del árbitro queda en la auditoría. Anular exige un motivo y guarda la versión anterior.

**Riesgo que aceptamos:** quien tenga el QR puede registrar eventos de ese partido. Por eso el QR se muestra solo al árbitro y se puede cambiar o desactivar en cualquier momento.

## Si usas un dominio propio de Supabase

`vercel.json` solo permite conectar con `https://*.supabase.co`. Si tu proyecto usa otro dominio, agrégalo en `connect-src`.
