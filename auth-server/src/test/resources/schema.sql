-- En produccion esta tabla la crea y la llena la app de Part38; aqui solo hace falta para los tests
create table if not exists users (
    id int auto_increment primary key,
    username varchar(255) not null unique,
    password varchar(255) not null,
    role varchar(50)
);
