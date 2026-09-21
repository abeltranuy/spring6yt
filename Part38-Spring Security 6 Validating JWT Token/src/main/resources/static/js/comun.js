// Funciones compartidas por login.js, registrar2.js y usuarios2.js

// Muestra un mensaje en el <div id="mensaje"> de la pagina. tipo: 'danger', 'success', 'warning'
function mostrarMensaje(texto, tipo) {
  const caja = document.getElementById('mensaje');
  if (!caja) {
    alert(texto);
    return;
  }
  caja.className = 'alert alert-' + (tipo || 'danger');
  // textContent y no innerHTML: el texto puede venir del servidor
  caja.textContent = texto;
}

// El backend devuelve los errores como {"message": "..."}
async function leerMensajeDeError(response, porDefecto) {
  try {
    const cuerpo = await response.json();
    return cuerpo.message || porDefecto;
  } catch (e) {
    return porDefecto;
  }
}
