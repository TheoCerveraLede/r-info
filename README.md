# r-Info

Reimplementación en Java del entorno educativo **r-Info 2.9.4**, hecha por
ingeniería inversa del `.jar` original (`r-Info2.9.4.jar`).

Incluye el lenguaje completo (scanner sensible a la indentación, parser,
intérprete concurrente) y un entorno gráfico con editor coloreado, ciudad
navegable, panel de robots y consola.

## Cómo se ejecuta

Necesita **JDK 21 o superior** (usa records sellados y `switch` con patrones).
La clase principal es `rinfo.ui.Main`.

### Desde IntelliJ

Abrir la carpeta como proyecto y ejecutar `src/rinfo/ui/Main.java` (botón
derecho → *Run 'Main.main()'*). El módulo ya viene con `src` como raíz de
fuentes y `out` como salida.

### Desde la consola

PowerShell:

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out rinfo.ui.Main
```

bash:

```bash
javac -encoding UTF-8 -d out $(find src -name '*.java')
java -cp out rinfo.ui.Main
```

### Sin ventana

Con un archivo como argumento el programa corre en modo texto:

```bash
java -Dstdout.encoding=UTF-8 -cp out rinfo.Rinfo ejemplos/hola.rinfo
```

`-Dstdout.encoding=UTF-8` sólo hace falta en Windows, para que los acentos
salgan bien en la consola. Acepta también `--velocidad <ms>` para demorar cada
acción.

## Cómo se usa la interfaz

| Tecla | Acción |
|---|---|
| `F9` | compilar (marca la línea del error) |
| `F5` | ejecutar, o continuar si está en pausa |
| `F7` | avanzar una acción por robot |

Con el mouse sobre la ciudad se colocan flores, papeles y obstáculos; el clic
derecho borra la esquina y la herramienta se elige en la barra superior. Los
sliders regulan la velocidad y el zoom. El panel de la derecha muestra la
posición y la bolsa de cada robot, y las variables del que esté seleccionado.

## Las bolsas de los robots

Los robots existen apenas **compilás** (`F9`), antes de ejecutar, con la bolsa
vacía. En el panel de la derecha las columnas **Flores** y **Papeles** son
editables mientras el programa está detenido: ahí se define con cuánto arranca
cada robot.

Ese valor sobrevive a las corridas. Al ejecutar, cada robot vuelve a su bolsa
configurada, así que se puede correr el mismo escenario varias veces sin
recompilar. Si en cambio tocás el fuente, la próxima ejecución recompila y las
bolsas vuelven a cero, avisándolo en la consola.

## Cómo se arma la ciudad sin el mouse

**Ciudad → Colocar…** (`Ctrl+L`) abre un diálogo no modal que se puede dejar
abierto mientras se arma el escenario:

- **En una esquina**: qué (flor, papel u obstáculo), cuántos, avenida y calle.
  En la avenida o en la calle se puede escribir `*` para que esa coordenada
  salga al azar: `avenida 3, calle *` deja la cantidad pedida en calles
  sorteadas de la avenida 3, y `* / *` la reparte por toda la ciudad. Con
  comodín cada unidad cae en una esquina distinta y no pisa las ocupadas.
- **En una zona**: se define el rectángulo con *desde av/ca* y *hasta av/ca*, y
  después se elige entre los dos repartos:
  - **en cada esquina de la zona**: deja la cantidad indicada en todas las
    esquinas del rectángulo. Es determinístico: 3 papeles en una zona de 12
    esquinas son 36 papeles.
  - **repartidos al azar en la zona**: distribuye ese total entre las esquinas
    del rectángulo. Con «una por esquina» marcado no apila ni pisa esquinas
    ocupadas, y avisa si no entraron todas.

  Un renglón debajo anticipa cuántas unidades va a poner cada modo, y
  **Vaciar zona** limpia el mismo rectángulo.

Un obstáculo y el contenido de una esquina se excluyen: poner un obstáculo
vacía la esquina, y poner flores o papeles saca el obstáculo.

Lo mismo desde la consola, que además hace el escenario reproducible:

```bash
java -cp out rinfo.Rinfo ejemplos/limpieza.rinfo --papel 1,2 --papel 1,4,3
java -cp out rinfo.Rinfo ejemplos/limpieza.rinfo --azar-papel 5,1,1,1,5
```

| Opción | Qué hace |
|---|---|
| `--ciudad AVxCA` | tamaño de la ciudad |
| `--flor AV,CA[,N]` | deja N flores en esa esquina (N por omisión 1) |
| `--papel AV,CA[,N]` | deja N papeles en esa esquina |
| `--obstaculo AV,CA` | pone un obstáculo |

En esas tres, `AV` y `CA` aceptan `*` para sortear esa coordenada:
`--papel 3,*,5` deja 5 papeles en 5 calles al azar de la avenida 3 y
`--papel '*,*,20'` los reparte por toda la ciudad. En bash conviene entrecomillar
el `*` para que no lo expanda el shell.

| `--zona-flor N[,AV1,CA1,AV2,CA2]` | deja N flores en **cada** esquina de la zona |
| `--zona-papel N[,…]` | ídem con papeles |
| `--zona-obstaculo N[,…]` | un obstáculo en cada esquina |
| `--azar-flor N[,…]` | reparte N flores **en total** al azar por la zona |
| `--azar-papel N[,…]` | ídem con papeles |
| `--azar-obstaculo N[,…]` | ídem con obstáculos |

Si se omite la zona se usa toda la ciudad. Las opciones se pueden repetir.
`java -cp out rinfo.Rinfo --ayuda` las lista.

```bash
java -cp out rinfo.Rinfo ejemplos/limpieza.rinfo --zona-papel 2,1,1,1,5
```

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

### Varios robots

Cada **variable de tipo robot** crea una instancia. Para tener más de un robot
alcanza con declarar más variables, y cada una corre en su propio hilo:

```
robots
  robot obrero
  comenzar
    mover
  fin
variables
  a, b, c: obrero          { tres robots del mismo tipo }
comenzar
  AsignarArea(a, zona1)
  AsignarArea(b, zona2)
  AsignarArea(c, zona3)
  Iniciar(a, 1, 1)
  Iniciar(b, 5, 1)
  Iniciar(c, 9, 1)
fin
```

También se pueden declarar varios tipos distintos en el bloque `robots` y
mezclarlos. Tres cosas para tener en cuenta:

- Cada robot necesita su `AsignarArea` antes del `Iniciar`, y las áreas no se
  pueden superponer. Si querés que compartan terreno, usá una sola `AreaC` y
  asignásela a todos.
- Dos robots no pueden estar en la misma esquina: si se cruzan, la corrida
  aborta con un choque.
- Los nombres que se usan en `EnviarMensaje` y `RecibirMensaje` son los de
  las variables, no los de los tipos.

Ejemplos: [`mensajes.rinfo`](ejemplos/mensajes.rinfo) y
[`esquina-compartida.rinfo`](ejemplos/esquina-compartida.rinfo).

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

#### El comodín `*`

`RecibirMensaje(v, *)` acepta un mensaje de **cualquier** emisor y se queda con
el más viejo de los que estén esperando. Sirve para un robot que atiende a
varios sin saber de antemano quién le va a hablar primero:

```
repetir 6
  RecibirMensaje(v, *)
  Informar('llego', v)
```

El comodín es sólo para recibir: `EnviarMensaje` necesita el nombre de un robot
concreto, no hay difusión a todos.

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

## Ejemplos

| Archivo | Qué muestra |
|---|---|
| [`hola.rinfo`](ejemplos/hola.rinfo) | lo mínimo: un robot que da una vuelta |
| [`procesos.rinfo`](ejemplos/procesos.rinfo) | procesos con parámetros `E`, `S` y `ES`, `si`/`sino`, `mientras` |
| [`varios-robots.rinfo`](ejemplos/varios-robots.rinfo) | tres instancias del mismo tipo, una por área |
| [`mensajes.rinfo`](ejemplos/mensajes.rinfo) | `EnviarMensaje` y `RecibirMensaje` entre dos robots |
| [`comodin.rinfo`](ejemplos/comodin.rinfo) | `RecibirMensaje(v, *)`: dos emisores, un receptor |
| [`esquina-compartida.rinfo`](ejemplos/esquina-compartida.rinfo) | `BloquearEsquina` sobre un `AreaC` compartida |
| [`limpieza.rinfo`](ejemplos/limpieza.rinfo) | junta papeles; se combina con las opciones de colocación |

Los que necesitan flores o papeles traen en un comentario la línea de comandos
con la que probarlos.

## Estructura del código

| Paquete | Contenido |
|---|---|
| `rinfo.lang` | `TipoToken`, `Token`, `Scanner`, `Parser` |
| `rinfo.ast` | árbol sintáctico: `Programa`, `Proceso`, `TipoRobot`, `Expr`, `Sent` |
| `rinfo.runtime` | `Ciudad`, `Robot`, `Area`, `Interprete`, `Simulacion`, monitores |
| `rinfo.ui` | `Main`, `EditorRInfo`, `VistaCiudad`, `PanelRobots` |

En [`INGENIERIA-INVERSA.md`](INGENIERIA-INVERSA.md) está el detalle de cómo se
obtuvo cada pieza del `.jar` y en qué se aparta esta versión del original.
