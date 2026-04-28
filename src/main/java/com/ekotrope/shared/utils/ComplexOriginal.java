package com.ekotrope.shared.utils;

public class ComplexOriginal
{
      double x, y;

      public ComplexOriginal(){
          x=Double.NaN;
          y=Double.NaN;
          }

      public ComplexOriginal(ComplexOriginal z){
          x=z.real();
          y=z.imaginary();
          }

      public ComplexOriginal(double x){
          this.x=x;
          y=0.0;
          }

      public ComplexOriginal(double x, double y){
          this.x=x;
          this.y=y;
          }

      public ComplexOriginal polar(){
         double r = Math.sqrt(this.x*this.x+this.y*this.y);
         double a = Math.atan2(this.y,this.x);
         return new ComplexOriginal(r,a);
         }

      public ComplexOriginal cartesian(){
          return new ComplexOriginal(this.x*Math.cos(this.y), this.x*Math.sin(this.y));
          }

      public double real(){
          return this.x;
          }

      public double imaginary(){
          return this.y;
          }

      public double magnitude(){
          return Math.sqrt(this.x*this.x+this.y*this.y);
          }

      public double argument(){
          return Math.atan2(this.y,this.x);
          }

      public ComplexOriginal add(ComplexOriginal z){
          return new ComplexOriginal(this.x+z.x, this.y+z.y);
          }

      public ComplexOriginal add(double d){
          return new ComplexOriginal(this.x+d, this.y);
          }

      public ComplexOriginal subtract(ComplexOriginal z){
          return new ComplexOriginal(this.x-z.x, this.y-z.y);
          }

      public ComplexOriginal subtract(double d){
          return new ComplexOriginal(this.x-d, this.y);
          }

      public ComplexOriginal negate(){
          return new ComplexOriginal(-this.x, -this.y);
          }

      public ComplexOriginal multiply(ComplexOriginal z){
          return new ComplexOriginal(this.x*z.x-this.y*z.y, this.x*z.y+this.y*z.x);
          }

      public ComplexOriginal multiply(double d){
          return new ComplexOriginal(this.x*d,this.y*d);
          }

      public ComplexOriginal divide(ComplexOriginal z){
          double r=z.x*z.x+z.y*z.y;
          return new ComplexOriginal((this.x*z.x+this.y*z.y)/r, (this.y*z.x-this.x*z.y)/r);
          }

      public ComplexOriginal divide(double d){
          return new ComplexOriginal(this.x/d,this.y/d);
          }

      public ComplexOriginal invert(){
          double r=this.x*this.x+this.y*this.y;
          return new ComplexOriginal(this.x/r, -this.y/r);
          }

      public ComplexOriginal conjugate(){
          return new ComplexOriginal(this.x, -this.y);
          }

      public double abs(){
          return  Math.sqrt(this.x*this.x+this.y*this.y);
          }

      public static ComplexOriginal parseComplex(String s){
          int from = s.indexOf('(');
          if(from==-1){
              return null;
          }
          int to = s.indexOf(',',from);
          double x = Double.parseDouble(s.substring(from+1,to));
          from = to;
          to = s.indexOf(')',from);
          double y = Double.parseDouble(s.substring(from+1,to));
          return new ComplexOriginal(x,y);
          }

      public ComplexOriginal exp(){
          double exp_x=Math.exp(this.x);
          return new ComplexOriginal(exp_x*Math.cos(this.y), exp_x*Math.sin(this.y));
          }

      public ComplexOriginal log(){
          double rpart=Math.sqrt(this.x*this.x+this.y*this.y);
          double ipart=Math.atan2(this.y,this.x);
          if(ipart>Math.PI){
              ipart=ipart-2.0*Math.PI;
          }
          return new ComplexOriginal(Math.log(rpart), ipart);}

      public ComplexOriginal sqrt(){
          double r=Math.sqrt(this.x*this.x+this.y*this.y);
          double rpart=Math.sqrt(0.5*(r+this.x));
          double ipart=Math.sqrt(0.5*(r-this.x));
          if(this.y<0.0){
              ipart=-ipart;
          }
          return new ComplexOriginal(rpart,ipart);
          }

      public ComplexOriginal pow(ComplexOriginal z){
          ComplexOriginal a=z.multiply(this.log());
          return a.exp();
          }

      public ComplexOriginal pow(double d){
          ComplexOriginal a=(this.log()).multiply(d);
          return a.exp();
          }

      public ComplexOriginal pow(int d){
          ComplexOriginal a=(this.log()).multiply(d);
          return a.exp();
          }

      public ComplexOriginal atan(){
          ComplexOriginal IM = new ComplexOriginal(0.0,-1.0);
          ComplexOriginal ZP = new ComplexOriginal(this.x,this.y-1.0);
          ComplexOriginal ZM = new ComplexOriginal(-this.x,-this.y-1.0);
          return IM.multiply(ZP.divide(ZM).log()).divide(2.0);
          }

      public ComplexOriginal atanh(){
          return (((this.add(1.0)).log()).subtract(((this.subtract(1.0)).negate()).log()).divide(2.0));
          }

}
