package com.ekotrope.shared.utils;

public class Complex
{
 // [Complex.java](http://Complex.java)
    /*
     * Copyright (c) 2003 Jon S. Squire.  All Rights Reserved.
     *
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions
     * are met:
     *
     * -Redistributions of source code must retain the above copyright
     *  notice, this list of conditions and the following disclaimer.
     *
     * -Redistribution in binary form must reproduce the above copyright
     *  notice, this list of conditions and the following disclaimer in
     *  the documentation and/or other materials provided with the distribution.
     *
     * Neither the name of the author or the names of contributors
     * may be used to endorse or promote products derived from this software
     * without specific prior written permission.
     *
     * This software is provided "AS IS," without a warranty of any kind. ALL
     * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
     * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
     * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. THE AUTHOR AND CONTRIBUTORS
     * SHALL NOT BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE
     * AS A RESULT OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE
     * SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL THE AUTHOR OR CONTRIBUTORS
     * OR SUCCEEDING LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA,
     * OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
     * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
     * ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF THE AUTHOR
     * OR CONTRIBUTORS HAVE BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
     *
     * You acknowledge that this software is not designed, licensed or
     * intended for use in the design, construction, operation or
     * maintenance of any human use medical device.
     */


    /** Immutable, complex numbers. A Complex consists of a real
     *  and imaginary part, called Cartesian coordinates.
     *
     *  The Complex class provides methods for arithmetic such as:
     *  add, subtract, multiply, divide, negate and invert.
     *  Also provided are complex functions sin, cos, tan, asin, acos, atan,
     *  sqrt, log, exp, pow, sinh, cosh, tanh, atanh.
     *
     *  Source code <a href="[Complex.java](http://Complex.java)">[Complex.java](http://Complex.java)</a>
     */

      double x, y; // Cartesian representation of complex

      /** cartesian coordinates real and imaginary are NaN */
      public Complex(){
          x=Double.NaN;
          y=Double.NaN;
          }

      /** construct a copy of a Complex object */
      public Complex(Complex z){
          x=z.real();
          y=z.imaginary();
          }

      /** real value, imaginary=0.0 */
      public Complex(double x){
          this.x=x;
          y=0.0;
          }

      /** cartesian coordinates real and imaginary */
      public Complex(double x, double y){
          this.x=x;
          this.y=y;
          }

      /** convert cartesian to polar */
      public Complex polar(){
         double lx=this.x, ly=this.y;
         double r = Math.sqrt(lx*lx+ly*ly);
         double a = Math.atan2(ly,lx);
         return new Complex(r,a);
         }

      /** convert polar to cartesian */
      public Complex cartesian(){
          return new Complex(this.x*Math.cos(this.y), this.x*Math.sin(this.y));
          }

      /** extract the real part of the complex number */
      public double real(){
          return this.x;
          }

      /** extract the imaginary part of the complex number */
      public double imaginary(){
          return this.y;
          }

      /** extract the magnitude of the complex number */
      public double magnitude(){
          double lx=this.x, ly=this.y;
          return Math.sqrt(lx*lx+ly*ly);
          }

      /** extract the argument of the complex number */
      public double argument(){
          return Math.atan2(this.y,this.x);
          }

      /** add complex numbers */
      public Complex add(Complex z){
          return new Complex(this.x+z.x, this.y+z.y);
          }

      /** add a double to a complex number */
      public Complex add(double d){
          return new Complex(this.x+d, this.y);
          }

      /** subtract z from the complex number */
      public Complex subtract(Complex z){
          return new Complex(this.x-z.x, this.y-z.y);
          }

      /** subtract the double d from the complex number */
      public Complex subtract(double d){
          return new Complex(this.x-d, this.y);
          }

      /** negate the complex number */
      public Complex negate(){
          return new Complex(-this.x, -this.y);
          }

      /** multiply complex numbers */
      public Complex multiply(Complex z){
          return new Complex(this.x*z.x-this.y*z.y, this.x*z.y+this.y*z.x);
          }

      /** multiply a complex number by a double */
      public Complex multiply(double d){
          return new Complex(this.x*d,this.y*d);
          }

      /** divide the complex number by z */
      public Complex divide(Complex z){
          double r=z.x*z.x+z.y*z.y;
          return new Complex((this.x*z.x+this.y*z.y)/r, (this.y*z.x-this.x*z.y)/r);
          }

      /** divide the complex number by the double d */
      public Complex divide(double d){
          return new Complex(this.x/d,this.y/d);
          }

      /** invert the complex number */
      public Complex invert(){
          double lx=this.x, ly=this.y;
          double r=lx*lx+ly*ly;
          return new Complex(lx/r, -ly/r);
          }

      /** conjugate the complex number */
      public Complex conjugate(){
          return new Complex(this.x, -this.y);
          }

      /** compute the absolute value of a complex number */
      public double abs(){
          double lx=this.x, ly=this.y;
          return  Math.sqrt(lx*lx+ly*ly);
          }

      //commented out bc it fails checkstyle
//      /** compare complex numbers for equality */
//      public boolean equals(Complex z){
//          return (z.x==this.x) && (z.y==this.y);
//          }

      /** convert text representation to a Complex.
       *  input format  (real_double,imaginary_double) */
      public static Complex parseComplex(String s){
          int from = s.indexOf('(');
          if(from==-1){
              return null;
          }
          int to = s.indexOf(',',from);
          double x = Double.parseDouble(s.substring(from+1,to));
          from = to;
          to = s.indexOf(')',from);
          double y = Double.parseDouble(s.substring(from+1,to));
          return new Complex(x,y);
          }

      /** compute e to the power of the complex number */
      public Complex exp(){
          double exp_x=Math.exp(this.x);
          return new Complex(exp_x*Math.cos(this.y), exp_x*Math.sin(this.y));
          }

      /** compute the natural logarithm of the complex number */
      public Complex log(){
          double lx=this.x, ly=this.y;
          double rpart=Math.sqrt(lx*lx+ly*ly);
          double ipart=Math.atan2(ly,lx);
          if(ipart>Math.PI){
              ipart=ipart-2.0*Math.PI;
          }
          return new Complex(Math.log(rpart), ipart);}

      /** compute the square root of the complex number */
      public Complex sqrt(){
          double lx=this.x, ly=this.y;
          double r=Math.sqrt(lx*lx+ly*ly);
          double rpart=Math.sqrt(0.5*(r+lx));
          double ipart=Math.sqrt(0.5*(r-lx));
          if(ly<0.0){
              ipart=-ipart;
          }
          return new Complex(rpart,ipart);
          }

      /** compute the complex number raised to the power z */
      public Complex pow(Complex z){
          // inline this.log() then z.multiply(log) then exp() to avoid intermediate allocations
          double lx=this.x, ly=this.y;
          double log_rpart=Math.sqrt(lx*lx+ly*ly);
          double log_ipart=Math.atan2(ly,lx);
          if(log_ipart>Math.PI){
              log_ipart=log_ipart-2.0*Math.PI;
          }
          double log_x=Math.log(log_rpart);
          double log_y=log_ipart;
          double a_x=z.x*log_x-z.y*log_y;
          double a_y=z.x*log_y+z.y*log_x;
          double exp_ax=Math.exp(a_x);
          return new Complex(exp_ax*Math.cos(a_y), exp_ax*Math.sin(a_y));
          }

      /** compute the complex number raised to the power double d */
      public Complex pow(double d){
          // inline this.log() then log.multiply(d) then exp() to avoid intermediate allocations
          double lx=this.x, ly=this.y;
          double log_rpart=Math.sqrt(lx*lx+ly*ly);
          double log_ipart=Math.atan2(ly,lx);
          if(log_ipart>Math.PI){
              log_ipart=log_ipart-2.0*Math.PI;
          }
          double a_x=Math.log(log_rpart)*d;
          double a_y=log_ipart*d;
          double exp_ax=Math.exp(a_x);
          return new Complex(exp_ax*Math.cos(a_y), exp_ax*Math.sin(a_y));
          }

      /** compute the complex number raised to the power double d */
      public Complex pow(int d){
          // inline this.log() then log.multiply(d) then exp() to avoid intermediate allocations
          double lx=this.x, ly=this.y;
          double log_rpart=Math.sqrt(lx*lx+ly*ly);
          double log_ipart=Math.atan2(ly,lx);
          if(log_ipart>Math.PI){
              log_ipart=log_ipart-2.0*Math.PI;
          }
          double a_x=Math.log(log_rpart)*d;
          double a_y=log_ipart*d;
          double exp_ax=Math.exp(a_x);
          return new Complex(exp_ax*Math.cos(a_y), exp_ax*Math.sin(a_y));
          }

      /** compute the arctangent of a complex number */
      public Complex atan(){
          // inline IM=(0,-1), ZP=(x,y-1), ZM=(-x,-y-1) then ZP.divide(ZM).log() then IM.multiply.divide(2)
          // to avoid allocating 3+ intermediate Complex objects
          double zp_x=this.x, zp_y=this.y-1.0;
          double zm_x=-this.x, zm_y=-this.y-1.0;
          double r=zm_x*zm_x+zm_y*zm_y;
          double div_x=(zp_x*zm_x+zp_y*zm_y)/r;
          double div_y=(zp_y*zm_x-zp_x*zm_y)/r;
          double log_rpart=Math.sqrt(div_x*div_x+div_y*div_y);
          double log_ipart=Math.atan2(div_y,div_x);
          if(log_ipart>Math.PI){
              log_ipart=log_ipart-2.0*Math.PI;
          }
          double lg_x=Math.log(log_rpart);
          double lg_y=log_ipart;
          // IM.multiply(lg): IM=(0,-1) => (0*lg_x-(-1)*lg_y, 0*lg_y+(-1)*lg_x)
          double mul_x=0.0*lg_x-(-1.0)*lg_y;
          double mul_y=0.0*lg_y+(-1.0)*lg_x;
          return new Complex(mul_x/2.0, mul_y/2.0);
          }

      /** compute the inverse hyperbolic tangent of a complex number */
        public Complex atanh(){
            // inline this.add(1).log() - this.subtract(1).negate().log(), all divided by 2
            // to avoid allocating 4+ intermediate Complex objects
            double add1_x=this.x+1.0, add1_y=this.y;
            double log1_rpart=Math.sqrt(add1_x*add1_x+add1_y*add1_y);
            double log1_ipart=Math.atan2(add1_y,add1_x);
            if(log1_ipart>Math.PI){
                log1_ipart=log1_ipart-2.0*Math.PI;
            }
            double log1_x=Math.log(log1_rpart);
            double log1_y=log1_ipart;
            double sub1_x=this.x-1.0, sub1_y=this.y;
            double neg_x=-sub1_x, neg_y=-sub1_y;
            double log2_rpart=Math.sqrt(neg_x*neg_x+neg_y*neg_y);
            double log2_ipart=Math.atan2(neg_y,neg_x);
            if(log2_ipart>Math.PI){
                log2_ipart=log2_ipart-2.0*Math.PI;
            }
            double log2_x=Math.log(log2_rpart);
            double log2_y=log2_ipart;
            double sub_x=log1_x-log2_x, sub_y=log1_y-log2_y;
            return new Complex(sub_x/2.0, sub_y/2.0);
            }

}
