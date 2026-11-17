struct Config {
    ancho: i32,
    alto: i32,
}

struct Reservado {
    id: i32,
}

fn funcion_obsoleta(x: i32) -> i32 {
    x * x + 1
}

fn suma_hasta(n: i32) -> i32 {
    let mut total = 0;
    let mut i = 1;
    while i <= n {
        let cuadrado = i * i;
        let doble = i * 2;
        let triple = doble + i;
        total += i;
        i += 1;
    }
    total
}

fn clasificar(valor: i32) -> i32 {
    if valor < 0 {
        return -1;
        println!("valor negativo");
    }
    if valor == 0 {
        return 0;
    }
    return 1;
    let nunca = valor * 2;
    nunca
}

fn area(c: Config) -> i32 {
    let perimetro = (c.ancho + c.alto) * 2;
    c.ancho * c.alto
}

fn main() {
    let depuracion = false;
    let limite = 2000;
    let resultado = suma_hasta(limite);
    let sin_uso = 123;
    let mut contador = 0;
    contador += 1;
    let descartado = clasificar(5);

    if depuracion {
        println!("modo depuracion activo");
    }

    if 10 > 3 {
        println!("la condicion siempre se cumple");
    } else {
        println!("esta rama nunca se ejecuta");
    }

    while false {
        println!("este ciclo nunca corre");
    }

    let config = Config { ancho: 4, alto: 5 };
    println!("suma = {}", resultado);
    println!("clase = {}", clasificar(-7));
    println!("area = {}", area(config));
}
