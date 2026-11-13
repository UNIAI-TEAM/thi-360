-- Keycloak dùng cơ sở dữ liệu riêng trong cùng máy chủ PostgreSQL dev.
-- Ứng dụng không bao giờ truy vấn cơ sở dữ liệu này (RULES.md mục 4).
CREATE DATABASE keycloak;
