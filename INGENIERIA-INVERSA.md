# Ingeniería inversa de `r-Info2.9.4.jar`

Notas de cómo se reconstruyó el lenguaje y el runtime a partir del `.jar`, y en
qué se aparta esta implementación del original.

## Método

No se usó ningún descompilador. El `.jar` (401 KB, 168 entradas, `Main-Class:
form.Main`, compilado con JDK 8 y Ant 1.10.8) se descomprimió y se leyó con
`javap` del JDK instalado:

```bash
unzip -q r-Info2.9.4.jar -d rinfo_x
javap -p  rinfo_x/**/*.class            # estructura: clases, campos, firmas
javap -c -p -constants form/Token.class # bytecode y constantes
```

Las firmas dieron la arquitectura; el bytecode de `form.Scanner`,
`form.Parser`, `form.Robot`, `form.Ciudad` y las clases de `arbol.*` dio la
gramática y la semántica exactas.

## El original

| Paquete | Contenido |
|---|---|
| `form` | scanner, parser, runtime (`Ciudad`, `Robot`, `Bolsa`, `Area*`), monitores de concurrencia y toda la GUI Swing |
| `arbol` | árbol sintáctico: `Programa`, `Proceso`, `RobotAST`, `Cuerpo`, declaraciones |
| `arbol.expresion` | expresiones y consultas del entorno |
| `arbol.expresion.operador.*` | una clase por operador, separadas en `aritmetico`, `booleano` y `relacional` |
| `arbol.llamada` | `Informar`, `Pos` e invocación de procesos |
| `arbol.sentencia` | asignación, selección, iteradores, invocación |
| `arbol.sentencia.primitiva` | `Mover`, `Derecha`, `TomarFlor`, `Iniciar`, `AsignarArea`, `EnviarMensaje`, … |

El intérprete original evalúa todo como `String`: los números van como texto
decimal y los booleanos como `"V"` y `"F"`.

## Tabla de tokens

`form.Token.spellings` tiene 80 entradas. El scanner nunca reconoce palabras
reservadas: devuelve `IDENTIFER` para cualquier palabra y es el **constructor
de `Token`** el que recorre `spellings[1..79]` buscando una coincidencia exacta
y reclasifica el token. Por eso el lenguaje distingue mayúsculas: `mover` es
una primitiva y `Mover` es un identificador.

La tabla completa está reproducida, en el mismo orden, en
[`TipoToken`](src/rinfo/lang/TipoToken.java).

## Scanner

Lo importante que reveló el bytecode de `form.Scanner.scan()`:

- El fuente se procesa con un `"\n"` agregado al final; al agotarse se lanza
  una excepción interna (`FinArchivo`) que se traduce a `EOT`.
- La indentación es significativa y **cada nivel son exactamente dos
  espacios**. Al empezar una línea se cuentan los espacios y se comparan con
  los de la línea anterior: la diferencia dividida por dos es la cantidad de
  `INDENT` o `DEDENT` sintéticos que se emiten antes del próximo token.
- Un número impar de espacios es error de indentación.
- Las líneas en blanco se saltean sin afectar el nivel.
- Una línea que arranca con `{` hereda el nivel de la anterior: los
  comentarios no abren ni cierran bloques.
- Un identificador es letra seguida de letras, dígitos, `.` o `_`.
- Los comentarios van entre `{` y `}`; el parser los descarta en `nextToken()`.

## Gramática

Reconstruida de `form.Parser`. Todos los bloques intermedios son opcionales:

```
programa      ::= "programa" ident [procesos] [areas] [robots] [variables] cuerpoPrograma
procesos      ::= "procesos" INDENT unProceso+ DEDENT
unProceso     ::= "proceso" ident [ "(" paramFormal (";" paramFormal)* ")" ]
                  [variables] cuerpo
paramFormal   ::= ("E" | "S" | "ES") ident ":" ("numero" | "boolean")
areas         ::= "areas" INDENT unArea+ DEDENT
unArea        ::= ident ":" ("AreaC"|"AreaP"|"AreaPC") "(" av1 "," ca1 "," av2 "," ca2 ")"
robots        ::= "robots" INDENT unRobot+ DEDENT
unRobot       ::= "robot" ident [variables] cuerpo
variables     ::= "variables" INDENT (ident ("," ident)* ":" tipo)+ DEDENT
cuerpo        ::= "comenzar" INDENT sentencia* DEDENT "fin"
cuerpoPrograma::= "comenzar" INDENT (asignarArea | iniciar)* DEDENT "fin"
```

Detalles que sólo se ven en el bytecode:

- `variables`, `comenzar` y `fin` de un proceso o de un robot van **al mismo
  nivel** que el `proceso` o el `robot`, no un nivel adentro.
- El cuerpo del programa principal **sólo** admite `AsignarArea` e `Iniciar`.
- `si`/`mientras`/`repetir` no llevan cierre: el bloque lo delimita el
  `DEDENT`. Los tokens `finSi`, `finmientras` y `finrepetir` existen en la
  tabla pero el parser no los usa.
- Las coordenadas de un área se evalúan al compilar, así que tienen que ser
  constantes, y se validan contra el rango 1..100 y contra el solapamiento con
  las áreas ya declaradas.
- Declarar una variable cuyo tipo es un tipo de robot **crea la instancia** del
  robot con el nombre de la variable (`form.Ciudad.addRobot`).

### Expresiones

`parseExpresion` es un *shunting-yard*: apila operadores, emite notación polaca
inversa y después `procesarToken` rearma el árbol desapilando. Dos cosas
particulares:

- La expresión termina cuando el token siguiente **cambia de línea**
  (`scanner.fil`). Eso es lo que permite escribir `si HayFlorEnLaEsquina` sin
  ningún delimitador.
- La precedencia (`thereIsHighOrEqualPrecedence`) es mínima: sólo `*` y `/`
  ligan más que `+` y `-`, y `~` liga más que `&` y `|`. Todo lo demás,
  incluidos los relacionales, comparte nivel y asocia a izquierda. Está
  reproducida tal cual en `Parser.desapilar`.
- El único operador unario es `~`.

## Runtime

- La ciudad es una matriz de 101x101 esquinas con los índices 1..100 en uso;
  cada esquina guarda flores, papeles y si tiene obstáculo.
- Direcciones en grados: `EAST = 0`, `NORTH = 90`, `WEAST = 180`, `SOUTH =
  270`. El norte incrementa la calle y el este la avenida; `derecha()` va
  norte → este → sur → oeste. El robot arranca mirando al norte.
- La vista invierte el eje vertical (`Ca2y`), así que la calle 1 queda abajo y
  el norte apunta hacia arriba.
- `AsignarArea` expande el rectángulo del área a la lista de esquinas que el
  robot tiene permitido pisar. `mover` y `Pos` fallan si el destino no está en
  esa lista, si se sale de la ciudad o si hay un obstáculo.
- **Un robot sin área asignada no puede iniciarse.**
- Los robots se crean al **compilar**, no al ejecutar: `parseVariables` llama a
  `Ciudad.addRobot` por cada variable de tipo robot. `form.Robot` guarda
  `floresEnBolsaDeConfiguracion` y `papelesEnBolsaDeConfiguracion`, y `reset()`
  restaura la bolsa desde ahí, que es lo que permite configurar el contenido
  inicial entre la compilación y la corrida.
- Después de cada movimiento se comprueba que ningún otro robot ocupe la misma
  esquina; si lo hay, es un choque y aborta la corrida.
- Cada robot corre en su propio hilo (`form.EjecucionRobot`); `Iniciar` clona
  el cuerpo y las variables del tipo de robot y lanza el hilo.
- La concurrencia se coordina con dos monitores: uno de mensajes
  (`EnviarMensaje` / `RecibirMensaje`) y uno de esquinas (`BloquearEsquina` /
  `LiberarEsquina`).
- En `RecibirMensaje` el remitente `*` es un comodín: `MonitorMensajes` compara
  el identificador con `"*"` y, si coincide, llama a `getValorByComodin()`, que
  se lleva el primer elemento de la lista `datos` —la más vieja de todas, sin
  importar quién la mandó— y si está vacía espera en la condición
  `esperaCualquiera`. El parser lo permite porque para el remitente no exige
  `IDENTIFER`: toma la escritura del token que venga, y `*` es `MULT`. Del lado
  de `EnviarMensaje` no hay comodín: el destino se resuelve con
  `Ciudad.getRobotByNombre`.
- Los procesos ven **solamente** sus parámetros formales y sus locales: no
  acceden a las variables del robot que los invoca.

## Correspondencia con esta implementación

| Original | Acá |
|---|---|
| `form.Token`, `form.Token.spellings` | `rinfo.lang.Token`, `rinfo.lang.TipoToken` |
| `form.Scanner` | `rinfo.lang.Scanner` |
| `form.Parser` | `rinfo.lang.Parser` |
| `arbol.Programa`, `Proceso`, `RobotAST`, `Cuerpo` | `rinfo.ast.Programa`, `Proceso`, `TipoRobot`, listas de `Sent` |
| `arbol.expresion.*` + `operador.*` (23 clases) | `rinfo.ast.Expr` (records anidados) + `Operador` + `Sensor` |
| `arbol.sentencia.*` + `primitiva.*` (21 clases) | `rinfo.ast.Sent` (records anidados) + `Primitiva` |
| `form.Ciudad`, `form.Bolsa`, `form.Area*` | `rinfo.runtime.Ciudad`, `Esquina`, `Area` + `TipoArea` |
| `form.Robot`, `form.Direction` | `rinfo.runtime.Robot`, `Direccion` |
| `form.Ejecucion`, `form.EjecucionRobot` | `rinfo.runtime.Simulacion`, `Interprete` |
| `form.MonitorMensajes`, `form.Dato` | `rinfo.runtime.Buzon` |
| `form.MonitorEsquinas` | `rinfo.runtime.MonitorEsquinas` |
| `form.MonitorActualizarVentana` | `rinfo.runtime.ControlEjecucion` |
| `form.Main`, `CodePanel`, `CiudadView`, `TablaRobot` | `rinfo.ui.Main`, `EditorRInfo`, `VistaCiudad`, `PanelRobots` |

## Diferencias deliberadas

Todo lo que sigue es más permisivo o más simple que el original; ningún
programa válido en r-Info deja de andar por esto.

1. **Valores tipados.** Los valores son `Integer` y `Boolean` en lugar de
   `String`. Se siguen mostrando como `V` y `F`.
2. **Paréntesis vacíos.** El original rechaza `proceso p()` y `p()`; acá se
   aceptan.
3. **`Informar` con un solo texto.** El original exige al menos una expresión
   después del literal; acá `Informar('listo')` es válido.
4. **Texto literal de varias palabras.** El original toma un único
   identificador entre comillas; acá se admite `'varias palabras'`. El literal
   se lexea como tokens y se vuelve a unir con un espacio, así que la
   puntuación queda separada: `'listo:'` se imprime como `listo :`.
5. **Variables inicializadas.** `numero` arranca en 0 y `boolean` en `F`, en
   vez de quedar sin valor.
6. **Errores con posición.** Los mensajes de compilación incluyen línea y
   columna, y la interfaz selecciona la línea del error.
7. **La GUI es nueva.** Se rehizo con Swing moderno (dibujo vectorial de la
   ciudad en vez de los PNG del `.jar`, editor con números de línea y
   coloreado derivado de `TipoToken`, panel de robots con inspector de
   variables). No se copió ningún recurso del original.
8. **Sin `Compe`/`Compedos`.** Las clases de competencia del original, que
   abrían sockets, no se reimplementaron.
9. **Armado de la ciudad.** El original tenía `form.Configuraciones` (una
   grilla de números) y `random.Ventana`, que quedó como prototipo sin
   terminar. Se reemplazaron por el diálogo *Ciudad → Colocar…* y por las
   opciones equivalentes de la línea de comandos.
