// Call the dataTables jQuery plugin
$(document).ready(async function() {
  // Ya no hay nada en localStorage: se le pregunta al servidor quien esta conectado.
  // Si no hay sesion responde 401 y pedir() manda al login.
  const request = await pedir('/me', { method: 'GET' });
  if (!request) {
    return;
  }
  const yo = await request.json();
  document.getElementById('txt-email-usuario').textContent = yo.username;

  await cargarUsuarios();
  // Despues de cargar las filas, para que DataTables las vea
  $('#usuarios').DataTable();
});

function volverAlLogin() {
  window.location.href = 'login.html?expirada=1';
}

// fetch hacia la API. La cookie de sesion viaja sola; cabecerasJson() agrega el token CSRF.
// Si el backend responde 401 (sin sesion o sesion vencida) vuelve al login.
async function pedir(url, opciones) {
  const request = await fetch(url, Object.assign({ headers: cabecerasJson() }, opciones));
  if (request.status === 401) {
    volverAlLogin();
    return null;
  }
  return request;
}

async function cargarUsuarios() {
  const request = await pedir('/students', { method: 'GET' });
  if (!request) {
    return;
  }
  if (!request.ok) {
    mostrarMensaje(await leerMensajeDeError(request, 'No se pudo cargar el listado.'), 'danger');
    return;
  }
  const usuarios = await request.json();

  // Las filas se arman con nodos y textContent, nunca concatenando HTML:
  // un nombre como <img src=x onerror=...> se muestra como texto y no se ejecuta.
  const tbody = document.querySelector('#usuarios tbody');
  tbody.replaceChildren();
  for (let usuario of usuarios) {
    const fila = document.createElement('tr');
    for (let valor of [usuario.id, usuario.name, usuario.marks]) {
      const celda = document.createElement('td');
      celda.textContent = valor;
      fila.appendChild(celda);
    }

    const botonEliminar = document.createElement('a');
    botonEliminar.href = '#';
    botonEliminar.className = 'btn btn-danger btn-circle btn-sm';
    botonEliminar.innerHTML = '<i class="fas fa-trash"></i>';
    botonEliminar.addEventListener('click', function(evento) {
      evento.preventDefault();
      eliminarUsuario(usuario.id);
    });
    const celdaAcciones = document.createElement('td');
    celdaAcciones.appendChild(botonEliminar);
    fila.appendChild(celdaAcciones);

    tbody.appendChild(fila);
  }
}

async function eliminarUsuario(id) {

  if (!confirm('¿Desea eliminar este usuario?')) {
    return;
  }

  const request = await pedir('/students/' + id, { method: 'DELETE' });
  if (!request) {
    return;
  }
  if (!request.ok) {
    mostrarMensaje(await leerMensajeDeError(request, 'No se pudo eliminar el usuario ' + id + '.'), 'danger');
    return;
  }

  location.reload()
}

// El logout tiene dos mitades. POST /logout cierra la sesion de esta app y devuelve la direccion
// del servidor de autorizacion donde se cierra la otra; hay que llevar el navegador alli, y el
// mismo servidor lo devuelve a login.html. Si solo se cerrara la sesion local, el siguiente
// "Iniciar Sesion" entraria directo sin pedir la clave.
async function logout() {
  try {
    const request = await fetch('/logout', { method: 'POST', headers: cabecerasJson() });
    if (request.ok) {
      const cuerpo = await request.json();
      window.location.href = cuerpo.logoutUrl;
      return;
    }
  } catch (e) {
    // sigue abajo
  }
  window.location.href = 'login.html';
}
