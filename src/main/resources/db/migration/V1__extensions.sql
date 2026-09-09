-- Extensions requises : PostGIS (géométrie), unaccent (normalisation recherche), pg_trgm (recherche texte)
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Fonction immutable pour index de recherche insensible aux diacritiques.
-- La normalisation ne modifie jamais le texte source (exigence i18n) : elle est appliquée
-- uniquement dans les expressions de recherche et leurs index.
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text AS
$$ SELECT public.unaccent('public.unaccent', $1) $$
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;
