$(document).ready(function() {
   // on ready
});


async function registrarUsuario() {
  let datos = {};
  datos.username = document.getElementById('txtNombre').value;
  datos.apellido = document.getElementById('txtApellido').value;
  datos.email = document.getElementById('txtEmail').value;
  datos.password = document.getElementById('txtPassword').value;

  let repetirPassword = document.getElementById('txtRepetirPassword').value;

  if (repetirPassword != datos.password) {
    mostrarMensaje('La contraseña que escribiste es diferente.', 'danger');
    return;
  }

  let request;
  try {
    request = await fetch('/registrar', {
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

  // 201 = creada. 409 = el usuario ya existe. 400 = datos invalidos (el backend dice cuales).
  if (!request.ok) {
    const texto = request.status === 409
      ? 'Ese nombre de usuario ya existe. Elige otro.'
      : await leerMensajeDeError(request, 'No se pudo crear la cuenta.');
    mostrarMensaje(texto, 'danger');
    return;
  }

  window.location.href = 'login.html?registrado=1'

}
