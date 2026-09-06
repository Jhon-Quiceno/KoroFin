# Convenciones del repositorio — KoroFin

Reglas obligatorias para commits, pull requests y comentarios de código.
Aplican tanto a personas como a asistentes de IA que trabajen en este repositorio.

## Regla general de idioma

- **Commits: siempre en español.**
- **Pull requests (título y descripción): siempre en español.**
- **Comentarios dentro del código: siempre en español** (español neutral/profesional).
- Los identificadores del código (clases, métodos, variables, nombres de archivo) se mantienen en inglés, como ya es costumbre en el proyecto.

## Estructura de commits

Se usa [Conventional Commits](https://www.conventionalcommits.org/es/):

```
<tipo>(<alcance opcional>): <descripción en español, en minúscula, sin punto final>

<cuerpo opcional: el "por qué" del cambio, en español>
```

### Tipos permitidos

| Tipo       | Cuándo usarlo                                              |
|------------|------------------------------------------------------------|
| `feat`     | Nueva funcionalidad                                         |
| `fix`      | Corrección de un bug                                        |
| `refactor` | Cambio de estructura/código sin alterar comportamiento      |
| `docs`     | Solo documentación                                          |
| `test`     | Agregar o corregir tests                                    |
| `chore`    | Tareas de mantenimiento (dependencias, configuración local) |
| `ci`       | Cambios en pipelines/despliegue                             |
| `style`    | Formato, sin cambio de lógica                               |
| `perf`     | Mejora de rendimiento                                       |

### Reglas

1. El tipo y el alcance van en inglés (estándar de la convención); **la descripción y el cuerpo van en español**.
2. Un commit = una unidad de trabajo revisable. No mezclar refactor con features en el mismo commit.
3. No se agrega `Co-Authored-By` ni ninguna atribución de IA.
4. El cuerpo explica el **por qué**, no el qué (el qué ya se ve en el diff).

### Ejemplos

```
refactor(backend): reorganizar paquetes por dominio de negocio
feat(deudas): agregar abono parcial a deudas
fix(auth): corregir expiración del refresh token
docs: agregar convenciones de commits y PRs
```

## Estructura de pull requests

- **Título**: mismo formato que un commit (`tipo(alcance): descripción en español`).
- **Rama base**: `develop`. Solo se abre PR a `main` para releases.
- **Descripción** con estas secciones:

```markdown
## Resumen
Qué hace este PR y por qué, en 2-4 líneas.

## Cambios
- Lista puntual de los cambios principales.

## Cómo probar
Pasos concretos para verificar que funciona.

## Notas
Decisiones tomadas, deuda técnica pendiente o cosas fuera de alcance.
(Omitir la sección si no hay nada que decir.)
```

- PRs pequeños: si el diff supera ~400 líneas de cambios reales, evaluar dividirlo en PRs encadenados.

## Comentarios en el código

1. **Siempre en español** (neutral/profesional, sin regionalismos).
2. Comentar solo lo que el código no puede decir por sí mismo: restricciones, decisiones no obvias, el "por qué".
3. No comentar lo evidente (`// incrementa el contador`), no dejar código muerto comentado.
4. Javadoc en clases y métodos públicos del backend: también en español.

## Flujo de ramas

- `main`: producción. Solo recibe merges desde `develop` (releases).
- `develop`: integración. Todas las ramas de trabajo salen de aquí y vuelven aquí vía PR.
- Nomenclatura de ramas: `feat/...`, `fix/...`, `refactor/...`, `docs/...` (en inglés o español, corto y descriptivo).
- Los refactors estructurales se mergean a `develop` lo antes posible una vez aprobados, para evitar conflictos con ramas de features abiertas.
