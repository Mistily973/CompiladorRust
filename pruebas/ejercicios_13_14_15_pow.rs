// Ejercicios 13, 14 y 15 de la guia, usando .pow() (Rust real) en vez de '^'
// (que en Rust es XOR a nivel de bits, no potencia).
// .pow() siempre requiere el exponente como u32: por eso d, y los exponentes
// calculados, se declaran o se castean como u32.

fn ejercicio13() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 2;
    let d: u32 = 2;
    let e: i32 = 1;
    let f: i32 = 3;
    let g: i32 = 1;
    let h: i32 = 2;
    let r = a + b * (c.pow(d) - e) / (f + g) - h;
}

fn ejercicio14() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 5;
    let d: i32 = 4;
    let e: i32 = 2;
    let r = ((a + b) / c).pow((d - e) as u32);
}

fn ejercicio15() {
    let a: i32 = 5;
    let b: i32 = 2;
    let c: i32 = 3;
    let d: i32 = 1;
    let e: i32 = 8;
    let f: i32 = 2;
    let r = ((a - b) * (c + d)).pow((e / f) as u32);
}

fn main() {
    ejercicio13();
    ejercicio14();
    ejercicio15();
}
