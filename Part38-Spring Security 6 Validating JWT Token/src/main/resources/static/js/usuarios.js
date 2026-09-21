// Call the dataTables jQuery plugin
$(document).ready(function() {
    cargarUsuarios();
  $('#usuarios').DataTable();
  actualizarEmailDelUsuario();
});

function actualizarEmailDelUsuario() {
    document.getElementById('txt-email-usuario').outerHTML = localStorage.email;
}

async function cargarUsuarios() {
  const request = await fetch('/students', {
    method: 'GET',
    headers: getHeaders()
  });
  const usuarios = await request.json();
  
  let listadoHtml = '';
  for (let usuario of usuarios) {
    let botonEliminar = '<a href="#" onclick="eliminarUsuario(' + usuario.id + ')" class="btn btn-danger btn-circle btn-sm"><i class="fas fa-trash"></i></a>';

    //let telefonoTexto = usuario.telefono == null ? '-' : usuario.telefono;
    let usuarioHtml = '<tr><td>' + usuario.id + '</td><td>' + usuario.name + '</td><td>'
                    + usuario.marks + '</td><td>' + botonEliminar + '</td></tr>';
					
    listadoHtml += usuarioHtml;
  }

document.querySelector('#usuarios tbody').outerHTML = listadoHtml;

}

function getHeaders() {
    return {
     'Accept': 'application/json',
     'Content-Type': 'application/json',
     'Authorization': 'Bearer ' + localStorage.token
   };
}

async function eliminarUsuario(id) {

  if (!confirm('¿Desea eliminar este usuario?')) {
    return;
  }

 const request = await fetch('/students/' + id, {
    method: 'DELETE',
    headers: getHeaders()
  });

  location.reload()
}

async function logout() {
alert("entre al logout usuarios");
 const request = await fetch('/logout', {
    method: 'POST',
    headers: getHeaders()
  });
  
  localStorage.token = "";
  localStorage.email = "";
  const contentType = request.headers.get('Authorization');
  alert(contentType);
  
  window.location.href = 'login.html'
}