create table weather_request_quota (
 provider varchar(16) not null,
 request_date date not null,
 calls integer not null,
 primary key(provider, request_date)
);
