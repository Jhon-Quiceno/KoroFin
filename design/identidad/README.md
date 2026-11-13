# Identidad de KoroFin

## La idea

**Una moneda repartida por una K: tu plata dividida en partes claras.**

El símbolo es un disco cortado por tres canales que dibujan una K. Quedan cuatro piezas; la cuña de la derecha va en verde y representa la parte que te queda.

## Archivos

| Archivo | Uso |
|---|---|
| `korofin-symbol.svg` | Maestro a color, sobre fondos claros |
| `korofin-symbol-reversed.svg` | Sobre fondos oscuros o índigo |
| `korofin-symbol-black.svg` / `korofin-symbol-white.svg` | Un solo color |
| `android/foreground.svg`, `android/legacy.svg` | Origen de los íconos del launcher de Android |
| `conceptos/` | Los tres conceptos de la primera ronda (se eligió C) |

En la app el símbolo se dibuja con `KoroMark` (`korofin_mobile/lib/widgets/brand/koro_mark.dart`), que usa la misma geometría del maestro.

## Colores

| Nombre | HEX | Uso |
|---|---|---|
| Índigo | `#4F46E5` | Color de marca, fondo del ícono de la app, botones |
| Esmeralda | `#059669` | Cuña del símbolo sobre fondos claros |
| Menta | `#6EE7B7` | Cuña del símbolo sobre fondos oscuros o índigo |
| Pizarra | `#0F172A` | Fondo oscuro de la app |

## Reglas de uso

- **Espacio libre:** deja alrededor del símbolo al menos el ancho de uno de sus canales (1/14 del diámetro).
- **Tamaño mínimo:** 16 px. A ese tamaño usa la versión de un solo color.
- **Nombre:** se escribe `KoroFin`, en Inter Bold, a la derecha del símbolo.
- **No hacer:** rotarlo, cambiar el color de otras piezas, agregarle sombras o degradados, ni ponerlo sobre fotos sin un fondo sólido.

## Pendientes

- El nombre en los SVG de `conceptos/` usa una fuente del sistema como texto vivo; no hay todavía un logotipo con las letras convertidas a trazos.
- No se hizo búsqueda de registro de marca. Antes de un lanzamiento público conviene una búsqueda profesional.
- Íconos de iOS sin actualizar (la app solo se distribuye en Android).
