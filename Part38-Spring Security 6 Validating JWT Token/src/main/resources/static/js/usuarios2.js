// Call the dataTables jQuery plugin
$(document).ready(async function() {
  // Sin token guardado no tiene sentido pedir nada
  if (!localStorage.token) {
    volverAlLogin();
    return;
  }
  actualizarEmailDelUsuario();
  await cargarUsuarios();
  // Despues de cargar las filas, para que DataTables las vea
  $('#usuarios').DataTable();
});

function actualizarEmailDelUsuario() {
    document.getElementById('txt-email-usuario').textContent = localStorage.email;
}

function volverAlLogin() {
  localStorage.removeItem("token");
  localStorage.removeItem("email");
  window.location.href = 'login.html?expirada=1';
}

// fetch con el token. Si el backend responde 401 (token vencido, revocado o invalido)
// limpia la sesion y vuelve al login en lugar de seguir con una respuesta de error.
async function pedir(url, opciones) {
  const request = await fetch(url, Object.assign({ headers: getHeaders() }, opciones));
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

function getHeaders() {
    return {
     'Accept': 'application/json',
     'Content-Type': 'application/json',
     'Authorization': localStorage.token
   };
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
    mostrarMensaje('No se pudo eliminar el usuario ' + id + ' (respuesta ' + request.status + ').', 'danger');
    return;
  }

  location.reload()
}

async function logout() {
  try {
    // keepalive: la peticion termina aunque el navegador ya este cambiando de pagina,
    // asi el token queda revocado en el servidor y no solo borrado aqui.
    await fetch('/logout', {
      method: 'POST',
      headers: getHeaders(),
      keepalive: true
    });
  } catch (e) {
    // Aunque falle la llamada, la sesion local se cierra igual
  }

  localStorage.removeItem("token");
  localStorage.removeItem("email");
  //localStorage.clear;

  window.location.href = 'login.html'
}
