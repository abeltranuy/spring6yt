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

function leerCookie(nombre) {
  const par = document.cookie.split('; ').find(c => c.startsWith(nombre + '='));
  return par ? decodeURIComponent(par.substring(nombre.length + 1)) : null;
}

// Cabeceras para fetch. La sesion viaja sola en una cookie HttpOnly que este JavaScript no puede
// leer (ya no hay token en localStorage). Lo que si se agrega es el token CSRF: el servidor lo deja
// en la cookie XSRF-TOKEN y hay que devolverlo en la cabecera X-XSRF-TOKEN; una pagina de otro
// sitio no puede leer esa cookie, asi que no puede falsificar la peticion.
function cabecerasJson() {
  const cabeceras = {
    'Accept': 'application/json',
    'Content-Type': 'application/json'
  };
  const csrf = leerCookie('XSRF-TOKEN');
  if (csrf) {
    cabeceras['X-XSRF-TOKEN'] = csrf;
  }
  return cabeceras;
}
