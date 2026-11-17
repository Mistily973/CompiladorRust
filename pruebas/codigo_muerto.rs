struct Punto {
    x: i32,
    y: i32,
}

struct Sobrante {
    dato: i32,
}

fn nunca_llamada(a: i32) -> i32 {
    a * 2
}

fn auxiliar(n: i32) -> i32 {
    let temporal = n * 3;
    return n + 1;
    let inalcanzable = n - 1;
    n * 100
}

fn main() {
    let usada = 10;
    let sin_uso = 20;
    let mut contador = 0;
    contador += 1;
    let calculo = auxiliar(usada);
    let descartado = auxiliar(5);
    let debug = false;
    if debug {
        println!("modo depuracion");
    }
    if 2 > 1 {
        println!("siempre se ejecuta");
    } else {
        println!("nunca se ejecuta");
    }
    while false {
        println!("nunca");
    }
    let p = Punto { x: 1, y: 2 };
    println!("{} {}", calculo, p.x);
}
