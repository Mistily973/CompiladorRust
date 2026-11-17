package GUI;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class EstilosCompilador {
    // --- PALETA DE COLORES ---
    public static final Color COLOR_FONDO          = new Color(255, 255, 255);
    public static final Color COLOR_SIDEBAR        = new Color(248, 250, 252);
    public static final Color COLOR_PRIMARIO       = new Color(30, 64, 175);
    public static final Color COLOR_EXITO          = new Color(5, 150, 105);
    public static final Color COLOR_ERROR          = new Color(220, 38, 38);
    public static final Color COLOR_TEXTO          = new Color(0, 0, 0);
    public static final Color COLOR_TEXTO_SUAVE    = new Color(107, 114, 128);
    public static final Color COLOR_BORDE          = new Color(226, 232, 240);
    public static final Color COLOR_GUTTER         = new Color(241, 245, 249);
    public static final Color COLOR_SELECCION      = new Color(219, 234, 254);

    // --- MAPEO DE NOMBRES DE TOKENS (acorde a RustLexerConfig) ---
    public static final Map<String, String> NOMBRES_TOKEN = new HashMap<>();

    static {
        NOMBRES_TOKEN.put("FN", "P.R. fn");
        NOMBRES_TOKEN.put("LET", "P.R. let");
        NOMBRES_TOKEN.put("MUT", "P.R. mut");
        NOMBRES_TOKEN.put("IF", "P.R. if");
        NOMBRES_TOKEN.put("ELSE", "P.R. else");
        NOMBRES_TOKEN.put("WHILE", "P.R. while");
        NOMBRES_TOKEN.put("FOR", "P.R. for");
        NOMBRES_TOKEN.put("LOOP", "P.R. loop");
        NOMBRES_TOKEN.put("IN", "P.R. in");
        NOMBRES_TOKEN.put("RETURN", "P.R. return");
        NOMBRES_TOKEN.put("STRUCT", "P.R. struct");
        NOMBRES_TOKEN.put("IMPL", "P.R. impl");
        NOMBRES_TOKEN.put("MATCH", "P.R. match");
        NOMBRES_TOKEN.put("BREAK", "P.R. break");
        NOMBRES_TOKEN.put("CONTINUE", "P.R. continue");
        NOMBRES_TOKEN.put("PUB", "P.R. pub");
        NOMBRES_TOKEN.put("SELF", "P.R. self");
        NOMBRES_TOKEN.put("AS", "P.R. as");
        NOMBRES_TOKEN.put("BOOLEANO", "Literal booleano");
        NOMBRES_TOKEN.put("TIPO_INT", "Tipo entero");
        NOMBRES_TOKEN.put("TIPO_FLOAT", "Tipo flotante");
        NOMBRES_TOKEN.put("TIPO_BOOL", "Tipo bool");
        NOMBRES_TOKEN.put("TIPO_CHAR", "Tipo char");
        NOMBRES_TOKEN.put("TIPO_STR", "Tipo String/str");
        NOMBRES_TOKEN.put("IDENTIFICADOR", "Identificador");
        NOMBRES_TOKEN.put("ENTERO", "Literal entero");
        NOMBRES_TOKEN.put("FLOTANTE", "Literal flotante");
        NOMBRES_TOKEN.put("STRING", "Literal cadena");
        NOMBRES_TOKEN.put("CARACTER", "Literal carácter");
        NOMBRES_TOKEN.put("OP_SUMA", "Operador suma");
        NOMBRES_TOKEN.put("OP_RESTA", "Operador resta");
        NOMBRES_TOKEN.put("OP_MULT", "Operador multiplicación");
        NOMBRES_TOKEN.put("OP_DIV", "Operador división");
        NOMBRES_TOKEN.put("OP_MOD", "Operador módulo");
        NOMBRES_TOKEN.put("OP_ASIG", "Operador de asignación");
        NOMBRES_TOKEN.put("OP_ASIG_COMP", "Operador de asignación compuesta");
        NOMBRES_TOKEN.put("OP_COMP", "Operador de comparación");
        NOMBRES_TOKEN.put("OP_LOG", "Operador lógico");
        NOMBRES_TOKEN.put("OP_NEG", "Operador de negación");
        NOMBRES_TOKEN.put("OP_REF", "Operador de referencia (&)");
        NOMBRES_TOKEN.put("FLECHA", "Flecha (->)");
        NOMBRES_TOKEN.put("FLECHA_GRUESA", "Flecha gruesa (=>)");
        NOMBRES_TOKEN.put("DOBLE_DOSPUNTOS", "Doble dos puntos (::)");
        NOMBRES_TOKEN.put("RANGO", "Operador de rango (..)");
        NOMBRES_TOKEN.put("PAR_A", "Paréntesis abre");
        NOMBRES_TOKEN.put("PAR_C", "Paréntesis cierra");
        NOMBRES_TOKEN.put("LLAVE_A", "Llave abre");
        NOMBRES_TOKEN.put("LLAVE_C", "Llave cierra");
        NOMBRES_TOKEN.put("CORCHETE_A", "Corchete abre");
        NOMBRES_TOKEN.put("CORCHETE_C", "Corchete cierra");
        NOMBRES_TOKEN.put("PUNTO_COMA", "Punto y coma");
        NOMBRES_TOKEN.put("COMA", "Coma");
        NOMBRES_TOKEN.put("DOS_PUNTOS", "Dos puntos");
        NOMBRES_TOKEN.put("PUNTO", "Punto");
        NOMBRES_TOKEN.put("COMENTARIO_BLOQUE", "Comentario de bloque");
        NOMBRES_TOKEN.put("COMENTARIO_NO_CERRADO", "ERROR: Comentario no cerrado");
    }

    // --- CÓDIGO DE EJEMPLO DE PRUEBA (análisis léxico) ---
    public static final String CODIGO_EJEMPLO_RUST =
        "fn main() {\n" +
        "    let x: i32 = 10;\n" +
        "    let y = 3.14;\n" +
        "    let error_num = 12.abc;\n" +
        "    let c = 'a';\n" +
        "    let error_char = 'abc';\n" +
        "    let s = \"Hola Rust\";\n" +
        "    let error_str = \"String sin cerrar;\n" +
        "    /* Comentario de bloque\n" +
        "       sin cerrar final\n" +
        "}";

    // --- CÓDIGO DE EJEMPLO SEMÁNTICO (con errores sembrados a propósito) ---
    public static final String CODIGO_EJEMPLO_SEMANTICO =
        "fn sumar(a: i32, b: i32) -> i32 {\n" +
        "    a + b\n" +
        "}\n\n" +
        "fn main() {\n" +
        "    let x: i32 = 10;\n" +
        "    x = 20;\n" +               // error: asignar a inmutable
        "    let resultado = sumar(x, \"hola\");\n" + // error: tipo de argumento
        "    let y = sumar(1, 2, 3);\n" + // error: número de argumentos
        "}";

    // --- CÓDIGO DE EJEMPLO DE CÓDIGO INTERMEDIO ---
    public static final String CODIGO_EJEMPLO_INTERMEDIO =
        "fn formula(a: f64, b: f64, c: f64) -> f64 {\n" +
        "    let discriminante = b * b - 4.0 * a * c;\n" +
        "    let resultado = (-b + discriminante) / (2.0 * a);\n" +
        "    resultado\n" +
        "}\n\n" +
        "fn main() {\n" +
        "    let x = 10;\n" +
        "    let y = 4;\n" +
        "    let total = x * y + x - y % 3;\n" +
        "    let r = formula(1.0, -3.0, 2.0);\n" +
        "}";


    public static final String CODIGO_EJEMPLO_CODIGO_MUERTO =
        "struct Punto {\n" +
        "    x: i32,\n" +
        "    y: i32,\n" +
        "}\n" +
        "\n" +
        "struct Sobrante {\n" +
        "    dato: i32,\n" +
        "}\n" +
        "\n" +
        "fn nunca_llamada(a: i32) -> i32 {\n" +
        "    a * 2\n" +
        "}\n" +
        "\n" +
        "fn auxiliar(n: i32) -> i32 {\n" +
        "    let temporal = n * 3;\n" +
        "    return n + 1;\n" +
        "    let inalcanzable = n - 1;\n" +
        "    n * 100\n" +
        "}\n" +
        "\n" +
        "fn main() {\n" +
        "    let usada = 10;\n" +
        "    let sin_uso = 20;\n" +
        "    let mut contador = 0;\n" +
        "    contador += 1;\n" +
        "    let calculo = auxiliar(usada);\n" +
        "    let descartado = auxiliar(5);\n" +
        "    let debug = false;\n" +
        "    if debug {\n" +
        "        println!(\"modo depuracion\");\n" +
        "    }\n" +
        "    if 2 > 1 {\n" +
        "        println!(\"siempre se ejecuta\");\n" +
        "    } else {\n" +
        "        println!(\"nunca se ejecuta\");\n" +
        "    }\n" +
        "    while false {\n" +
        "        println!(\"nunca\");\n" +
        "    }\n" +
        "    let p = Punto { x: 1, y: 2 };\n" +
        "    println!(\"{} {}\", calculo, p.x);\n" +
        "}";


    public static final String CODIGO_EJEMPLO_CONSTANT_FOLDING =
        "fn calcular_area() -> i32 {\n" +
        "    let ancho = 4;\n" +
        "    let alto = 5;\n" +
        "    let area = ancho * alto;\n" +
        "    let margen = 2 + 3;\n" +
        "    area + margen\n" +
        "}\n" +
        "\n" +
        "fn formula(x: i32) -> i32 {\n" +
        "    let a = 2;\n" +
        "    let b = 3;\n" +
        "    let c = 4;\n" +
        "    let constante = (a + b) * c;\n" +
        "    x * (10 - 4) + constante\n" +
        "}\n" +
        "\n" +
        "fn promedio() -> f64 {\n" +
        "    let total = 10 as f64;\n" +
        "    let cantidad = 4 as f64;\n" +
        "    total / cantidad\n" +
        "}\n" +
        "\n" +
        "fn potencias() -> i32 {\n" +
        "    let base = 2 + 1;\n" +
        "    let exp = 4;\n" +
        "    base.pow(exp as u32) + (2 * 3).pow(2)\n" +
        "}\n" +
        "\n" +
        "fn main() {\n" +
        "    let modo = 5 > 3;\n" +
        "    let limite = 100 / 4 + 5 % 3;\n" +
        "    let mut suma = 0;\n" +
        "    let mut i = 0;\n" +
        "    while i < limite {\n" +
        "        suma += i * (2 + 2);\n" +
        "        i += 1;\n" +
        "    }\n" +
        "\n" +
        "    if modo && 1 < 2 {\n" +
        "        println!(\"modo activo\");\n" +
        "    }\n" +
        "\n" +
        "    println!(\"area = {}\", calcular_area());\n" +
        "    println!(\"formula = {}\", formula(7));\n" +
        "    println!(\"promedio = {}\", promedio());\n" +
        "    println!(\"potencias = {}\", potencias());\n" +
        "    println!(\"suma = {}\", suma);\n" +
        "}";


    public static final String CODIGO_EJEMPLO_OPTIMIZACIONES =
        "struct Config {\n" +
        "    ancho: i32,\n" +
        "    alto: i32,\n" +
        "}\n" +
        "\n" +
        "struct Reservado {\n" +
        "    id: i32,\n" +
        "}\n" +
        "\n" +
        "fn funcion_obsoleta(x: i32) -> i32 {\n" +
        "    x * x + 1\n" +
        "}\n" +
        "\n" +
        "fn suma_hasta(n: i32) -> i32 {\n" +
        "    let mut total = 0;\n" +
        "    let mut i = 1;\n" +
        "    while i <= n {\n" +
        "        let cuadrado = i * i;\n" +
        "        let doble = i * 2;\n" +
        "        let triple = doble + i;\n" +
        "        total += i;\n" +
        "        i += 1;\n" +
        "    }\n" +
        "    total\n" +
        "}\n" +
        "\n" +
        "fn clasificar(valor: i32) -> i32 {\n" +
        "    if valor < 0 {\n" +
        "        return -1;\n" +
        "        println!(\"valor negativo\");\n" +
        "    }\n" +
        "    if valor == 0 {\n" +
        "        return 0;\n" +
        "    }\n" +
        "    return 1;\n" +
        "    let nunca = valor * 2;\n" +
        "    nunca\n" +
        "}\n" +
        "\n" +
        "fn area(c: Config) -> i32 {\n" +
        "    let perimetro = (c.ancho + c.alto) * 2;\n" +
        "    c.ancho * c.alto\n" +
        "}\n" +
        "\n" +
        "fn main() {\n" +
        "    let depuracion = false;\n" +
        "    let limite = 2000;\n" +
        "    let resultado = suma_hasta(limite);\n" +
        "    let sin_uso = 123;\n" +
        "    let mut contador = 0;\n" +
        "    contador += 1;\n" +
        "    let descartado = clasificar(5);\n" +
        "\n" +
        "    if depuracion {\n" +
        "        println!(\"modo depuracion activo\");\n" +
        "    }\n" +
        "\n" +
        "    if 10 > 3 {\n" +
        "        println!(\"la condicion siempre se cumple\");\n" +
        "    } else {\n" +
        "        println!(\"esta rama nunca se ejecuta\");\n" +
        "    }\n" +
        "\n" +
        "    while false {\n" +
        "        println!(\"este ciclo nunca corre\");\n" +
        "    }\n" +
        "\n" +
        "    let config = Config { ancho: 4, alto: 5 };\n" +
        "    println!(\"suma = {}\", resultado);\n" +
        "    println!(\"clase = {}\", clasificar(-7));\n" +
        "    println!(\"area = {}\", area(config));\n" +
        "}";
}
