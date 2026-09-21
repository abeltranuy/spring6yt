$(document).ready(async function() {
  // El servidor de autorizacion no acepta "localhost" como direccion de retorno, y la cookie de
  // sesion queda ligada al nombre con que se abrio la pagina: todo el flujo debe usar 127.0.0.1.
  if (window.location.hostname === 'localhost') {
    window.location.replace(window.location.href.replace('//localhost', '//127.0.0.1'));
    return;
  }

  // Avisos que dejan las otras paginas (o Spring) al redirigir aqui
  const parametros = new URLSearchParams(window.location.search);
  if (parametros.has('registrado')) {
    mostrarMensaje('La cuenta fue creada con exito. Ya puedes iniciar sesion.', 'success');
  } else if (parametros.has('expirada')) {
    mostrarMensaje('Tu sesion expiro o no es valida. Inicia sesion de nuevo.', 'warning');
  } else if (parametros.has('error')) {
    mostrarMensaje('No se pudo completar el inicio de sesion. Intenta de nuevo.', 'danger');
  }

  // Si ya hay sesion no tiene sentido mostrar el login
  try {
    const request = await fetch('/me', { headers: { 'Accept': 'application/json' } });
    if (request.ok) {
      window.location.href = 'usuarios.html';
    }
  } catch (e) {
    mostrarMensaje('No se pudo conectar con el servidor.', 'danger');
  }
});

// El usuario y la clave ya no se escriben en esta pagina. El boton "Iniciar Sesion" es un enlace a
// /oauth2/authorization/auth-server: Spring lleva el navegador al servidor de autorizacion, alli se
// escribe la clave, y al volver esta app crea la sesion y redirige a usuarios.html.
