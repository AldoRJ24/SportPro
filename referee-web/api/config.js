// Función de Vercel: entrega a la página la URL de Supabase y la clave PUBLICABLE.
//
// Las dos se configuran en Vercel → Settings → Environment Variables, así nunca quedan
// escritas en el repositorio. La clave publicable es pública por diseño (también va dentro
// del APK): no da acceso a nada por sí sola. NUNCA pongas aquí la secret key ni service_role.
module.exports = (req, res) => {
  const url = process.env.SUPABASE_URL || '';
  const key = process.env.SUPABASE_PUBLISHABLE_KEY || '';
  // Defensa extra: si por error se configuró una clave secreta, no se entrega.
  const looksSecret = key.startsWith('sb_secret_') || key.includes('service_role');
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  res.status(200).send(JSON.stringify(looksSecret ? { url: '', key: '' } : { url, key }));
};
