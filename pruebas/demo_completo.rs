// ============================================================
// Programa de prueba completo para el compilador de Rust.
// Pasa los tres análisis (léxico, sintáctico y semántico) sin
// ningún error. Está pensado para que le hagas cambios y veas
// cómo el analizador semántico detecta los errores.
// ============================================================

struct Punto {
    x: f64,
    y: f64,
}

struct Rectangulo {
    esquina: Punto,
    ancho: f64,
    alto: f64,
}

struct Persona {
    nombre: String,
    edad: u32,
    activo: bool,
}

// --------------------------------------------------------------
// Funciones simples: parámetros, retorno y recursión
// --------------------------------------------------------------

fn suma(a: i32, b: i32) -> i32 {
    a + b
}

fn resta(a: i32, b: i32) -> i32 {
    a - b
}

fn promedio(a: f64, b: f64) -> f64 {
    (a + b) / 2.0
}

fn factorial(n: u64) -> u64 {
    if n <= 1 {
        1
    } else {
        n * factorial(n - 1)
    }
}

fn es_par(n: i32) -> bool {
    n % 2 == 0
}

fn maximo(a: i32, b: i32) -> i32 {
    if a > b { a } else { b }
}

// --------------------------------------------------------------
// Funciones que reciben referencias (préstamos) y las modifican
// --------------------------------------------------------------

fn duplicar(x: &mut i32) {
    *x = *x * 2;
}

fn incrementar_edad(p: &mut Persona) {
    p.edad += 1;
}

fn area(r: &Rectangulo) -> f64 {
    r.ancho * r.alto
}

fn escalar(r: &mut Rectangulo, factor: f64) {
    r.ancho *= factor;
    r.alto *= factor;
}

fn distancia_origen(p: &Punto) -> f64 {
    let cuadrado = p.x * p.x + p.y * p.y;
    cuadrado.sqrt()
}

// --------------------------------------------------------------
// Funciones sobre colecciones (Vec)
// --------------------------------------------------------------

fn suma_vector(v: &Vec<i32>) -> i32 {
    let mut total = 0;
    for elemento in v {
        total += *elemento;
    }
    total
}

fn contar_pares(v: &Vec<i32>) -> u32 {
    let mut contador = 0;
    for elemento in v {
        if es_par(*elemento) {
            contador += 1;
        }
    }
    contador
}

fn primero_o_cero(v: &Vec<i32>) -> i32 {
    if v.is_empty() {
        0
    } else {
        v[0]
    }
}

// --------------------------------------------------------------
// Funciones con match, tuplas y desestructuración
// --------------------------------------------------------------

fn clasificar(n: i32) -> String {
    match n {
        0 => String::from("cero"),
        1..=9 => String::from("un dígito"),
        _ => String::from("grande"),
    }
}

fn dividir_con_resto(a: i32, b: i32) -> (i32, i32) {
    let cociente = a / b;
    let resto = a % b;
    (cociente, resto)
}

fn presentar_persona(p: &Persona) -> String {
    format!("{} tiene {} años", p.nombre, p.edad)
}

// --------------------------------------------------------------
// Struct con inicialización, campos y métodos de biblioteca
// --------------------------------------------------------------

fn crear_persona(nombre: &str, edad: u32) -> Persona {
    Persona {
        nombre: String::from(nombre),
        edad: edad,
        activo: true,
    }
}

fn crear_rectangulo(ancho: f64, alto: f64) -> Rectangulo {
    Rectangulo {
        esquina: Punto { x: 0.0, y: 0.0 },
        ancho: ancho,
        alto: alto,
    }
}

// --------------------------------------------------------------
// main: usa todo lo anterior
// --------------------------------------------------------------

fn main() {
    // Variables simples y aritmética
    let x = 10;
    let y = 20;
    let mut total = suma(x, y);
    total = resta(total, 5);
    println!("Total: {}", total);

    // Flotantes
    let a: f64 = 3.5;
    let b: f64 = 7.5;
    let prom = promedio(a, b);
    println!("Promedio: {}", prom);

    // Recursión
    let fact5 = factorial(5);
    println!("5! = {}", fact5);

    // Booleanos y condicionales como expresión
    let numero = 14;
    let par = es_par(numero);
    let mayor = maximo(numero, 20);
    println!("{} es par: {}, mayor con 20: {}", numero, par, mayor);

    // Mutabilidad y referencias
    let mut contador = 5;
    duplicar(&mut contador);
    println!("Contador duplicado: {}", contador);

    // Structs anidados
    let mut persona = crear_persona("Ana", 29);
    incrementar_edad(&mut persona);
    println!("{}", presentar_persona(&persona));

    let mut rect = crear_rectangulo(4.0, 5.0);
    escalar(&mut rect, 2.0);
    let area_total = area(&rect);
    println!("Área del rectángulo: {}", area_total);

    let punto = Punto { x: 3.0, y: 4.0 };
    let dist = distancia_origen(&punto);
    println!("Distancia al origen: {}", dist);

    // Vectores
    let mut numeros = Vec::new();
    numeros.push(1);
    numeros.push(2);
    numeros.push(3);
    numeros.push(4);
    numeros.push(5);
    let suma_total = suma_vector(&numeros);
    let pares = contar_pares(&numeros);
    let primero = primero_o_cero(&numeros);
    println!("Suma: {}, pares: {}, primero: {}", suma_total, pares, primero);

    // Arreglos y slices
    let arreglo = [10, 20, 30, 40, 50];
    let mut suma_arreglo = 0;
    for valor in &arreglo {
        suma_arreglo += *valor;
    }
    println!("Suma del arreglo: {}", suma_arreglo);

    // Match y tuplas
    let categoria = clasificar(numero);
    println!("Categoría de {}: {}", numero, categoria);

    let (cociente, resto) = dividir_con_resto(17, 5);
    println!("17 / 5 = {} con resto {}", cociente, resto);

    // Ciclos while y loop
    let mut i = 0;
    while i < 3 {
        println!("Iteración while: {}", i);
        i += 1;
    }

    let mut acumulado = 0;
    let resultado_loop = loop {
        acumulado += 1;
        if acumulado == 4 {
            break acumulado * 10;
        }
    };
    println!("Resultado del loop: {}", resultado_loop);

    // Rango en for
    for k in 0..3 {
        println!("Rango: {}", k);
    }

    // Cast entre tipos numéricos
    let entero: i32 = 9;
    let flotante: f64 = entero as f64;
    let de_vuelta: i32 = flotante as i32;
    println!("Cast: {} -> {} -> {}", entero, flotante, de_vuelta);

    // Bloque con su propio ámbito
    let doble_total = {
        let temporal = total * 2;
        temporal + 1
    };
    println!("Doble total + 1: {}", doble_total);

    println!("Fin del programa. Persona activa: {}", persona.activo);
}
