$(document).ready(function() {
  // Avisos que dejan las otras paginas al redirigir aqui
  const parametros = new URLSearchParams(window.location.search);
  if (parametros.has('registrado')) {
    mostrarMensaje('La cuenta fue creada con exito. Ya puedes iniciar sesion.', 'success');
  } else if (parametros.has('expirada')) {
    mostrarMensaje('Tu sesion expiro o no es valida. Inicia sesion de nuevo.', 'warning');
  }
});

async function iniciarSesion() {
  let datos = {};
  datos.username = document.getElementById('txtEmail').value;
  datos.password = document.getElementById('txtPassword').value;

  let request;
  try {
    request = await fetch('/login', {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(datos)
    });
  } catch (e) {
    mostrarMensaje('No se pudo conectar con el servidor.', 'danger');
    return;
  }

  // El backend responde 200 con el token en la cabecera Authorization ("Bearer ..."),
  // o 401/400 con {"message": "..."}. Hay que mirar el estado, no el texto del body.
  const token = request.headers.get('Authorization');
  if (!request.ok || !token) {
    const porDefecto = 'Las credenciales son incorrectas. Por favor intente nuevamente.';
    const texto = request.status === 401 ? porDefecto : await leerMensajeDeError(request, porDefecto);
    mostrarMensaje(texto, 'danger');
    return;
  }

  localStorage.setItem("token", token);
  localStorage.setItem("email", datos.username);

  window.location.href = 'usuarios.html'
}
