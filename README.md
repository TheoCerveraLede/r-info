# r-Info

Reimplementación en Java del entorno educativo **r-Info 2.9.4**, hecha por
ingeniería inversa del `.jar` original (`r-Info2.9.4.jar`).

Incluye el lenguaje completo (scanner sensible a la indentación, parser,
intérprete concurrente) y un entorno gráfico con editor coloreado, ciudad
navegable, panel de robots y consola.

## Cómo se usa

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out rinfo.ui.Main
```

Sin argumentos abre la interfaz. Para correr un programa sin ventana:

```bash
java -cp out rinfo.Rinfo ejemplos/hola.rinfo
```

En la interfaz: **F9** compila, **F5** ejecuta, **F7** avanza un paso por robot.
Con el mouse sobre la ciudad se colocan flores, papeles y obstáculos (clic
derecho borra); la herramienta se elige en la barra superior.

## El lenguaje

```
programa <nombre>
procesos      (opcional)
areas         (opcional)
robots        (opcional)
variables     (opcional)
comenzar
  AsignarArea(<robot>, <area>)
  Iniciar(<robot>, <avenida>, <calle>)
fin
```

Los bloques se delimitan **por indentación**: exactamente dos espacios por
nivel. Un renglón que arranca con `{` es comentario y no altera el nivel.

El cuerpo del programa principal sólo asigna áreas e inicia robots: toda la
lógica vive dentro de los robots, y **cada robot corre en su propio hilo**.

### Declaraciones

```
areas
  zona: AreaP(1, 1, 10, 10)     { AreaC compartida, AreaP privada, AreaPC parcial }

robots
  robot obrero
  variables
    pasos: numero
  comenzar
    ...
  fin

variables
  r1: obrero                    { variable de tipo robot: crea la instancia }
  n: numero
  ok: boolean
```

Un robot sólo puede pisar las esquinas de las áreas que se le asignaron: sin
`AsignarArea` no arranca. Dos áreas no se pueden superponer.

### Procesos

```
procesos
  proceso avanzar(E cuadras: numero; S juntadas: numero)
  variables
    i: numero
  comenzar
    ...
  fin
```

`E` copia el valor al entrar, `S` lo devuelve al salir y `ES` hace las dos
cosas. Un proceso ve únicamente sus parámetros y sus locales, no las variables
del robot que lo invoca.

### Sentencias

| Sentencia | Significado |
|---|---|
| `v:= <expresión>` | asignación |
| `si <cond>` / `sino` | selección |
| `mientras <cond>` | iteración condicional |
| `repetir <n>` | iteración incondicional |
| `p(a, b)` | invocación de proceso |

### Primitivas

`mover`, `derecha`, `tomarFlor`, `tomarPapel`, `depositarFlor`,
`depositarPapel`, `Pos(av, ca)`, `Informar('texto', expr, ...)`, `Leer(v)`,
`Random(v, desde, hasta)`, `EnviarMensaje(expr, robot)`,
`RecibirMensaje(v, robot)`, `BloquearEsquina(av, ca)`, `LiberarEsquina(av, ca)`.

### Consultas

`PosAv`, `PosCa`, `HayObstaculo`, `HayFlorEnLaEsquina`, `HayFlorEnLaBolsa`,
`HayPapelEnLaEsquina`, `HayPapelEnLaBolsa`.

### Operadores

`+ - * /` aritméticos, `& | ~` booleanos, `= <> < > <= >=` relacionales,
`V` y `F` como literales booleanos.

La precedencia es la del intérprete original y es más chata de lo que uno
espera: sólo `*` y `/` ligan más que `+` y `-`, y `~` liga más que `&` y `|`.
Todo lo demás, relacionales incluidos, comparte nivel y asocia a izquierda, así
que **hay que poner paréntesis** alrededor de las comparaciones:

```
ok:= (i > 3) & (i <= 100)     { bien }
ok:= i > 3 + 1                { se lee ((i > 3) + 1): error de tipos }
```

## Estructura del código

| Paquete | Contenido |
|---|---|
| `rinfo.lang` | `TipoToken`, `Token`, `Scanner`, `Parser` |
| `rinfo.ast` | árbol sintáctico: `Programa`, `Proceso`, `TipoRobot`, `Expr`, `Sent` |
| `rinfo.runtime` | `Ciudad`, `Robot`, `Area`, `Interprete`, `Simulacion`, monitores |
| `rinfo.ui` | `Main`, `EditorRInfo`, `VistaCiudad`, `PanelRobots` |

En [`INGENIERIA-INVERSA.md`](INGENIERIA-INVERSA.md) está el detalle de cómo se
obtuvo cada pieza del `.jar` y en qué se aparta esta versión del original.
