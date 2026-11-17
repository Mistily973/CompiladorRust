fn ejercicio1() {
    let a: i32 = 2;
    let b: i32 = 3;
    let r = a + b;
}

fn ejercicio2() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 4;
    let r = a + b * c;
}

fn ejercicio3() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 4;
    let r = (a + b) * c;
}

fn ejercicio4() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 8;
    let d: i32 = 4;
    let r = a * b + c / d;
}

fn ejercicio5() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 8;
    let d: i32 = 4;
    let r = (a + b) * (c - d);
}

fn ejercicio6() {
    let a: i32 = 2;
    let b: i32 = 5;
    let c: i32 = 3;
    let d: i32 = 4;
    let r = a + (b - c) * d;
}

fn ejercicio7() {
    let a: i32 = 10;
    let b: i32 = 4;
    let c: i32 = 3;
    let d: i32 = 2;
    let r = (a - b) / c + d;
}

fn ejercicio8() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 4;
    let d: i32 = 8;
    let e: i32 = 4;
    let r = a * (b + c) - d / e;
}

fn ejercicio9() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 8;
    let d: i32 = 4;
    let e: i32 = 1;
    let r = (a + b) / (c - d) + e;
}

fn ejercicio10() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 8;
    let d: i32 = 4;
    let e: i32 = 2;
    let r = a + b * (c - d) / e;
}

fn ejercicio11() {
    let a: i32 = 2;
    let b: i32 = 3;
    let c: i32 = 4;
    let d: i32 = 5;
    let r = ((a + b) * c) - d;
}

fn ejercicio12() {
    let a: i32 = 10;
    let b: i32 = 4;
    let c: i32 = 3;
    let d: i32 = 2;
    let e: i32 = 9;
    let f: i32 = 4;
    let r = (a - b) * (c + d) / (e - f);
}

// Los ejercicios 13, 14 y 15 de la guía usan '^' (potencia), que no se
// incluye aquí: en Rust real '^' es el operador bit a bit XOR (no potencia),
// así que se dejan fuera para mantener el compilador apegado a Rust.
// Si se necesitara probarlos, '^' tendría que reescribirse con .pow(), por
// ejemplo: c.pow(d as u32) en vez de c ^ d.

fn main() {
    ejercicio1();
    ejercicio2();
    ejercicio3();
    ejercicio4();
    ejercicio5();
    ejercicio6();
    ejercicio7();
    ejercicio8();
    ejercicio9();
    ejercicio10();
    ejercicio11();
    ejercicio12();
}
